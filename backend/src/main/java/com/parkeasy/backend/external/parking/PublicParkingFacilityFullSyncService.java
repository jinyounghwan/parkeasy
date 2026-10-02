package com.parkeasy.backend.external.parking;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PublicParkingFacilityFullSyncService {
	public static final String FULL_CONFIRMATION = "FACILITY_FULL_SYNC";
	public static final int MAX_LIMITED_PAGES = 100;
	private static final Logger log = LoggerFactory.getLogger(PublicParkingFacilityFullSyncService.class);

	private final PublicParkingClient client;
	private final PublicParkingFacilityFullPageService pageService;
	private final ParkingSyncJobService jobService;
	private final ParkingSyncSkippedPageRepository skippedPageRepository;
	private final TaskExecutor taskExecutor;
	private final FullSyncDelay delay;
	private final int numOfRows;
	private final long pageDelayMs;
	private final boolean autoResumeEnabled;
	private final long autoResumeDelayMs;
	private final int maxAutoResumeAttempts;
	private final int emptyPageSkipThreshold;
	private final int trailingEmptyPageThreshold;
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicLong activeJobId = new AtomicLong();
	private final AtomicBoolean pauseRequested = new AtomicBoolean();
	private final PublicDataFailureClassifier failureClassifier = new PublicDataFailureClassifier();

	public PublicParkingFacilityFullSyncService(PublicParkingClient client,
			PublicParkingFacilityFullPageService pageService, ParkingSyncJobService jobService,
			ParkingSyncSkippedPageRepository skippedPageRepository,
			@Qualifier("parkingFullSyncTaskExecutor") TaskExecutor taskExecutor, FullSyncDelay delay,
			@Value("${parkeasy.public-data.full-sync.facility.num-of-rows:1000}") int numOfRows,
			@Value("${parkeasy.public-data.full-sync.facility.page-delay-ms:1000}") long pageDelayMs,
			@Value("${parkeasy.public-data.full-sync.facility.auto-resume-enabled:true}") boolean autoResumeEnabled,
			@Value("${parkeasy.public-data.full-sync.facility.auto-resume-delay-ms:60000}") long autoResumeDelayMs,
			@Value("${parkeasy.public-data.full-sync.facility.max-auto-resume-attempts:5}") int maxAutoResumeAttempts,
			@Value("${parkeasy.public-data.full-sync.facility.empty-page-skip-threshold:3}") int emptyPageSkipThreshold,
			@Value("${parkeasy.public-data.full-sync.facility.trailing-empty-page-threshold:10}") int trailingEmptyPageThreshold) {
		if (numOfRows < 1 || numOfRows > 1000) throw new IllegalArgumentException("Facility full sync numOfRows must be between 1 and 1000");
		if (pageDelayMs < 0 || autoResumeDelayMs < 0) throw new IllegalArgumentException("Facility full sync delays must not be negative");
		if (maxAutoResumeAttempts < 0 || maxAutoResumeAttempts > 100) throw new IllegalArgumentException("maxAutoResumeAttempts must be between 0 and 100");
		if (emptyPageSkipThreshold < 1 || emptyPageSkipThreshold > 100) throw new IllegalArgumentException("emptyPageSkipThreshold must be between 1 and 100");
		if (trailingEmptyPageThreshold < 1 || trailingEmptyPageThreshold > 100) throw new IllegalArgumentException("trailingEmptyPageThreshold must be between 1 and 100");
		this.client = client; this.pageService = pageService; this.jobService = jobService;
		this.skippedPageRepository = skippedPageRepository;
		this.taskExecutor = taskExecutor; this.delay = delay; this.numOfRows = numOfRows;
		this.pageDelayMs = pageDelayMs; this.autoResumeEnabled = autoResumeEnabled;
		this.autoResumeDelayMs = autoResumeDelayMs; this.maxAutoResumeAttempts = maxAutoResumeAttempts;
		this.emptyPageSkipThreshold = emptyPageSkipThreshold;
		this.trailingEmptyPageThreshold = trailingEmptyPageThreshold;
	}

	public ParkingFacilityFullSyncStatus start(Integer maxPages, int startPage, boolean full, String confirmation) {
		Integer pageLimit = validateExecution(maxPages, startPage, full, confirmation);
		acquire();
		try {
			ParkingSyncJob job = jobService.create(startPage, numOfRows, pageLimit);
			jobService.markRunning(job.getId());
			ParkingFacilityFullSyncStatus accepted = snapshot(job.getId());
			submit(job.getId());
			return accepted;
		} catch (RuntimeException exception) {
			running.set(false); throw exception;
		}
	}

	public ParkingFacilityFullSyncStatus resume(Integer maxPages, boolean full, String confirmation) {
		return resume(null, maxPages, full, confirmation);
	}

	public ParkingFacilityFullSyncStatus resume(Long jobId, Integer maxPages, boolean full, String confirmation) {
		Integer additionalPageLimit = validateExecution(maxPages, 1, full, confirmation);
		acquire();
		try {
			ParkingSyncJob job = jobId == null ? jobService.latestResumable() : jobService.requiredResumable(jobId);
			jobService.prepareExplicitResume(job.getId(), additionalPageLimit);
			pageService.bootstrapTrailingEmptyPages(job.getId(), trailingEmptyPageThreshold);
			ParkingFacilityFullSyncStatus accepted = snapshot(job.getId());
			submit(job.getId());
			return accepted;
		} catch (RuntimeException exception) {
			running.set(false); throw exception;
		}
	}

	public ParkingFacilityFullSyncStatus requestPause() {
		if (running.get()) pauseRequested.set(true);
		long jobId = activeJobId.get();
		return jobId == 0 ? status() : status(jobId);
	}

	public ParkingFacilityFullSyncStatus status() { return status(null); }

	public ParkingFacilityFullSyncStatus status(Long jobId) {
		return view(jobId == null ? jobService.latest() : jobService.required(jobId));
	}

	public ParkingFacilityFullSyncStatus repairPrematureCompletion(long jobId) {
		if (running.get()) throw new FullSyncAlreadyRunningException();
		jobService.repairPrematureCompletion(jobId);
		return snapshot(jobId);
	}

	private void submit(long jobId) {
		pauseRequested.set(false);
		activeJobId.set(jobId);
		try {
			taskExecutor.execute(() -> runWorker(jobId));
		} catch (RuntimeException exception) {
			activeJobId.compareAndSet(jobId, 0);
			running.set(false);
			throw exception;
		}
	}

	private void runWorker(long jobId) {
		log.info("FACILITY_FULL_SYNC event=JOB_STARTED jobId={} pageNo={}", jobId, jobService.required(jobId).getNextPage());
		try {
			runPages(jobId);
		} catch (RuntimeException exception) {
			ParkingSyncJob job = jobService.required(jobId);
			PublicDataFailure failure = failureClassifier.classify(exception);
			jobService.markFailed(jobId, job.getNextPage(), failure);
			log.info("FACILITY_FULL_SYNC event=JOB_FAILED jobId={} pageNo={} failureCategory={} rootErrorType={}",
					jobId, job.getNextPage(), failure.category(), failure.rootErrorType());
		} finally {
			activeJobId.compareAndSet(jobId, 0);
			running.set(false);
		}
	}

	private void runPages(long jobId) {
		while (true) {
			ParkingSyncJob before = jobService.required(jobId);
			if (before.getConsecutiveEmptyPageCount() >= trailingEmptyPageThreshold) {
				completeForTrailingEmptyPages(jobId, before.getLastSuccessPage());
				return;
			}
			if (pauseRequested.get()) { pause(jobId, before.getNextPage()); return; }
			int pageNo = before.getNextPage();
			jobService.markPageStarted(jobId, pageNo);
			long startedNanos = System.nanoTime();
			try {
				PublicParkingFacilityResponse response = client.fetchFacilities(pageNo, before.getNumOfRows());
				long totalCount = before.getTotalCount() == null ? parseTotalCount(response.totalCount()) : before.getTotalCount();
				long totalPages = calculateTotalPages(totalCount, before.getNumOfRows());
				validatePageCompleteness(pageNo, totalPages, totalCount, before.getNumOfRows(), response);
				ParkingFacilityFullPageResult result = pageService.process(jobId, pageNo, response, totalCount, totalPages);
				ParkingSyncJob after = jobService.required(jobId);
				log.info("FACILITY_FULL_SYNC event=PAGE_COMMITTED jobId={} pageNo={} durationMs={} raw={} inserted={} updated={} skipped={} duplicates={}",
						jobId, pageNo, elapsedMillis(startedNanos), result.processed(), result.inserted(),
						result.updated(), result.skipped(), result.duplicates());
				if (pageNo >= totalPages) { complete(jobId, pageNo); return; }
				if (pauseRequested.get() || after.reachedPageLimit()) { pause(jobId, pageNo); return; }
				if (!delay.await(pageDelayMs, pauseRequested::get)) { pause(jobId, pageNo); return; }
			} catch (RuntimeException failure) {
				PublicDataFailure classified = failureClassifier.classify(failure);
				IncompletePublicDataPageException incomplete = failure instanceof IncompletePublicDataPageException value
						? value : null;
				if (incomplete != null && incomplete.emptySuccessCandidate()) {
					jobService.recordEmptySuccessFailure(jobId, pageNo, classified);
				} else if (incomplete != null && incomplete.actualRows() > 0) {
					jobService.recordPartialPageFailure(jobId, pageNo, classified);
				} else {
					jobService.recordPageFailure(jobId, pageNo, classified);
				}
				ParkingSyncJob failed = jobService.required(jobId);
				log.info("FACILITY_FULL_SYNC event=PAGE_FAILED jobId={} pageNo={} durationMs={} failureCategory={} rootErrorType={} attempt={} emptySuccessCount={}",
						jobId, pageNo, elapsedMillis(startedNanos), classified.category(),
						classified.rootErrorType(), failed.getAutoResumeAttempts(), failed.getCurrentEmptySuccessCount());
				if (incomplete != null && incomplete.emptySuccessCandidate()
						&& failed.getCurrentEmptySuccessCount() >= emptyPageSkipThreshold) {
					if (!skipPermanentEmptyPage(jobId, pageNo, failed, incomplete)) return;
					continue;
				}
				if (!scheduleAutoResume(jobId, pageNo, failure, classified)) return;
			}
		}
	}

	private boolean scheduleAutoResume(long jobId, int pageNo, RuntimeException cause,
			PublicDataFailure failure) {
		ParkingSyncJob job = jobService.required(jobId);
		if (!autoResumeEnabled || !failure.transientFailure()
				|| job.getAutoResumeAttempts() >= maxAutoResumeAttempts) {
			jobService.markFailed(jobId, pageNo, failure);
			log.info("FACILITY_FULL_SYNC event=JOB_FAILED jobId={} pageNo={} attempt={} failureCategory={} rootErrorType={}",
					jobId, pageNo, job.getAutoResumeAttempts(), failure.category(), failure.rootErrorType());
			return false;
		}
		int nextAttempt = job.getAutoResumeAttempts() + 1;
		log.info("FACILITY_FULL_SYNC event=AUTO_RESUME_SCHEDULED jobId={} pageNo={} attempt={} delayMs={}",
				jobId, pageNo, nextAttempt, autoResumeDelayMs);
		if (!delay.await(autoResumeDelayMs, pauseRequested::get)) { pause(jobId, pageNo); return false; }
		if (pauseRequested.get()) { pause(jobId, pageNo); return false; }
		jobService.markAutoResume(jobId);
		log.info("FACILITY_FULL_SYNC event=AUTO_RESUME_STARTED jobId={} pageNo={} attempt={}", jobId, pageNo, nextAttempt);
		return true;
	}

	private boolean skipPermanentEmptyPage(long jobId, int pageNo, ParkingSyncJob job,
			IncompletePublicDataPageException incomplete) {
		long totalCount = incomplete.totalCount();
		long totalPages = incomplete.totalPages();
		pageService.skipEmptyPage(jobId, pageNo, job.getCurrentEmptySuccessCount(), totalCount, totalPages);
		log.info("FACILITY_FULL_SYNC event=PAGE_SKIPPED jobId={} pageNo={} reason=EMPTY_SUCCESS_PAGE emptySuccessCount={}",
				jobId, pageNo, job.getCurrentEmptySuccessCount());
		ParkingSyncJob after = jobService.required(jobId);
		if (pageNo >= totalPages) { complete(jobId, pageNo); return false; }
		if (after.getConsecutiveEmptyPageCount() >= trailingEmptyPageThreshold) {
			completeForTrailingEmptyPages(jobId, pageNo);
			return false;
		}
		if (pauseRequested.get() || after.reachedPageLimit()) { pause(jobId, pageNo); return false; }
		if (!delay.await(pageDelayMs, pauseRequested::get)) { pause(jobId, pageNo); return false; }
		return true;
	}

	private void pause(long jobId, int pageNo) {
		jobService.markPaused(jobId);
		log.info("FACILITY_FULL_SYNC event=JOB_PAUSED jobId={} pageNo={}", jobId, pageNo);
	}

	private void complete(long jobId, int pageNo) {
		jobService.markCompleted(jobId);
		log.info("FACILITY_FULL_SYNC event=JOB_COMPLETED jobId={} pageNo={}", jobId, pageNo);
	}

	private void completeForTrailingEmptyPages(long jobId, int pageNo) {
		jobService.markCompletedForTrailingEmptyPages(jobId);
		log.info("FACILITY_FULL_SYNC event=JOB_COMPLETED_WITH_GAPS jobId={} pageNo={} completionReason={} threshold={}",
				jobId, pageNo, ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES,
				trailingEmptyPageThreshold);
	}

	long calculateTotalPages(long totalCount, int rows) {
		if (totalCount < 0) throw new IllegalStateException("Public data totalCount is invalid");
		return totalCount == 0 ? 0 : ((totalCount - 1) / rows) + 1;
	}

	private void validatePageCompleteness(int pageNo, long totalPages, long totalCount,
			int expectedRows, PublicParkingFacilityResponse response) {
		int actualRows = response.safeItems().size();
		boolean confirmedSuccess = "0".equals(response.resultCode())
				&& "SUCCESS".equalsIgnoreCase(response.resultMsg());
		if (actualRows == 0 && totalCount > 0) {
			throw new IncompletePublicDataPageException(pageNo, 0, expectedRows,
					confirmedSuccess, totalCount, totalPages);
		}
		if (pageNo < totalPages && actualRows < expectedRows) {
			throw new IncompletePublicDataPageException(pageNo, actualRows, expectedRows,
					false, totalCount, totalPages);
		}
	}

	private long parseTotalCount(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 0) throw new NumberFormatException("negative");
			return parsed;
		} catch (NumberFormatException | NullPointerException exception) {
			throw new IllegalStateException("Public data totalCount is missing or invalid");
		}
	}

	private Integer validateExecution(Integer maxPages, int startPage, boolean full, String confirmation) {
		if (startPage < 1) throw new IllegalArgumentException("startPage must be at least 1");
		if (full) {
			if (!FULL_CONFIRMATION.equals(confirmation)) throw new IllegalArgumentException("Unlimited sync requires explicit confirmation");
			return null;
		}
		if (maxPages == null || maxPages < 1 || maxPages > MAX_LIMITED_PAGES) {
			throw new IllegalArgumentException("maxPages is required and must be between 1 and " + MAX_LIMITED_PAGES);
		}
		return maxPages;
	}

	private void acquire() { if (!running.compareAndSet(false, true)) throw new FullSyncAlreadyRunningException(); }
	private ParkingFacilityFullSyncStatus snapshot(long jobId) { return view(jobService.required(jobId)); }
	private ParkingFacilityFullSyncStatus view(ParkingSyncJob job) {
		var skippedPages = skippedPageRepository.findTop10ByJobIdOrderByCreatedAtDesc(job.getId())
				.stream().map(ParkingSyncSkippedPageSummary::from).toList();
		boolean workerRunning = running.get() && job.getId() == activeJobId.get();
		return ParkingFacilityFullSyncStatus.from(job, maxAutoResumeAttempts,
				emptyPageSkipThreshold, trailingEmptyPageThreshold, workerRunning, skippedPages);
	}
	private long elapsedMillis(long startedNanos) { return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(); }
}
