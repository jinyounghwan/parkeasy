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
public class PublicParkingOperationFullSyncService {
	public static final String FULL_CONFIRMATION = "OPERATION_FULL_SYNC";
	private static final int MAX_LIMITED_PAGES = 100;
	private static final Logger log = LoggerFactory.getLogger(PublicParkingOperationFullSyncService.class);
	private final PublicParkingOperationClient client;
	private final PublicParkingOperationFullPageService pageService;
	private final ParkingSyncJobService jobService;
	private final ParkingSyncSkippedPageRepository skippedPageRepository;
	private final TaskExecutor executor;
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

	public PublicParkingOperationFullSyncService(PublicParkingOperationClient client,
			PublicParkingOperationFullPageService pageService, ParkingSyncJobService jobService,
			ParkingSyncSkippedPageRepository skippedPageRepository,
			@Qualifier("parkingOperationFullSyncTaskExecutor") TaskExecutor executor, FullSyncDelay delay,
			@Value("${parkeasy.public-data.full-sync.operation.num-of-rows:1000}") int numOfRows,
			@Value("${parkeasy.public-data.full-sync.operation.page-delay-ms:1000}") long pageDelayMs,
			@Value("${parkeasy.public-data.full-sync.operation.auto-resume-enabled:true}") boolean autoResumeEnabled,
			@Value("${parkeasy.public-data.full-sync.operation.auto-resume-delay-ms:60000}") long autoResumeDelayMs,
			@Value("${parkeasy.public-data.full-sync.operation.max-auto-resume-attempts:5}") int maxAutoResumeAttempts,
			@Value("${parkeasy.public-data.full-sync.operation.empty-page-skip-threshold:3}") int emptyPageSkipThreshold,
			@Value("${parkeasy.public-data.full-sync.operation.trailing-empty-page-threshold:10}") int trailingEmptyPageThreshold) {
		if (numOfRows < 1 || numOfRows > 1000) throw new IllegalArgumentException("Operation numOfRows must be between 1 and 1000");
		if (pageDelayMs < 0 || autoResumeDelayMs < 0) throw new IllegalArgumentException("Operation delays must not be negative");
		if (maxAutoResumeAttempts < 0 || maxAutoResumeAttempts > 100) throw new IllegalArgumentException("Operation maxAutoResumeAttempts must be between 0 and 100");
		if (emptyPageSkipThreshold < 1 || emptyPageSkipThreshold > 100 || trailingEmptyPageThreshold < 1 || trailingEmptyPageThreshold > 100) {
			throw new IllegalArgumentException("Operation empty thresholds must be between 1 and 100");
		}
		this.client = client; this.pageService = pageService; this.jobService = jobService;
		this.skippedPageRepository = skippedPageRepository; this.executor = executor; this.delay = delay;
		this.numOfRows = numOfRows; this.pageDelayMs = pageDelayMs; this.autoResumeEnabled = autoResumeEnabled;
		this.autoResumeDelayMs = autoResumeDelayMs; this.maxAutoResumeAttempts = maxAutoResumeAttempts;
		this.emptyPageSkipThreshold = emptyPageSkipThreshold; this.trailingEmptyPageThreshold = trailingEmptyPageThreshold;
	}

	public ParkingOperationFullSyncStatus start(Integer maxPages, int startPage, boolean full, String confirmation) {
		Integer limit = validateExecution(maxPages, startPage, full, confirmation); acquire();
		try {
			ParkingSyncJob job = jobService.createOperation(startPage, numOfRows, limit);
			jobService.markRunning(job.getId());
			ParkingOperationFullSyncStatus accepted = snapshot(job.getId()); submit(job.getId()); return accepted;
		} catch (RuntimeException exception) { running.set(false); throw exception; }
	}

	public ParkingOperationFullSyncStatus resume(Long jobId, Integer maxPages, boolean full, String confirmation) {
		Integer limit = validateExecution(maxPages, 1, full, confirmation); acquire();
		try {
			ParkingSyncJob job = jobId == null ? jobService.latestOperationResumable()
					: jobService.requiredOperationResumable(jobId);
			jobService.prepareExplicitResume(job.getId(), limit); pageService.bootstrapTrailingEmptyPages(job.getId());
			ParkingOperationFullSyncStatus accepted = snapshot(job.getId()); submit(job.getId()); return accepted;
		} catch (RuntimeException exception) { running.set(false); throw exception; }
	}

	public ParkingOperationFullSyncStatus pause() {
		if (running.get()) pauseRequested.set(true);
		long id = activeJobId.get(); return id == 0 ? status(null) : status(id);
	}

	public ParkingOperationFullSyncStatus status(Long jobId) {
		ParkingSyncJob job = jobId == null ? jobService.latestOperation() : jobService.required(jobId);
		if (!ParkingSyncJob.OPERATION_FULL.equals(job.getSyncType())) throw new IllegalArgumentException("Sync job is not an operation full sync job");
		return view(job);
	}

	private void submit(long jobId) {
		pauseRequested.set(false); activeJobId.set(jobId);
		try { executor.execute(() -> runWorker(jobId)); }
		catch (RuntimeException exception) { activeJobId.compareAndSet(jobId, 0); running.set(false); throw exception; }
	}

	private void runWorker(long jobId) {
		try { runPages(jobId); }
		catch (RuntimeException exception) {
			ParkingSyncJob job = jobService.required(jobId); PublicDataFailure failure = failureClassifier.classify(exception);
			jobService.markFailed(jobId, job.getNextPage(), failure);
			log.info("OPERATION_FULL_SYNC event=JOB_FAILED jobId={} pageNo={} failureCategory={} rootErrorType={}",
					jobId, job.getNextPage(), failure.category(), failure.rootErrorType());
		} finally { activeJobId.compareAndSet(jobId, 0); running.set(false); }
	}

	private void runPages(long jobId) {
		while (true) {
			ParkingSyncJob before = jobService.required(jobId);
			if (before.getConsecutiveEmptyPageCount() >= trailingEmptyPageThreshold) { completeTrailing(jobId); return; }
			if (pauseRequested.get()) { pauseJob(jobId); return; }
			int pageNo = before.getNextPage(); jobService.markPageStarted(jobId, pageNo); long started = System.nanoTime();
			try {
				PublicParkingOperationResponse response = client.fetchOperations(pageNo, before.getNumOfRows());
				long totalCount = before.getTotalCount() == null ? parseTotalCount(response.totalCount()) : before.getTotalCount();
				long totalPages = calculateTotalPages(totalCount, before.getNumOfRows());
				validateCompleteness(pageNo, totalPages, totalCount, before.getNumOfRows(), response);
				ParkingOperationFullPageResult result = pageService.process(jobId, pageNo, response, totalCount, totalPages);
				ParkingSyncJob after = jobService.required(jobId);
				log.info("OPERATION_FULL_SYNC event=PAGE_COMMITTED jobId={} pageNo={} durationMs={} processed={} inserted={} updated={} orphan={} invalid={} duplicates={}",
						jobId, pageNo, elapsed(started), result.processed(), result.inserted(), result.updated(),
						result.orphanCount(), result.invalidOperationCount(), result.duplicates());
				if (pageNo >= totalPages) { jobService.markCompleted(jobId); return; }
				if (pauseRequested.get() || after.reachedPageLimit()) { pauseJob(jobId); return; }
				if (!delay.await(pageDelayMs, pauseRequested::get)) { pauseJob(jobId); return; }
			} catch (RuntimeException exception) {
				PublicDataFailure failure = failureClassifier.classify(exception);
				IncompletePublicDataPageException incomplete = exception instanceof IncompletePublicDataPageException value ? value : null;
				if (incomplete != null && incomplete.emptySuccessCandidate()) jobService.recordEmptySuccessFailure(jobId, pageNo, failure);
				else if (incomplete != null && incomplete.actualRows() > 0) jobService.recordPartialPageFailure(jobId, pageNo, failure);
				else jobService.recordPageFailure(jobId, pageNo, failure);
				ParkingSyncJob failed = jobService.required(jobId);
				if (incomplete != null && incomplete.emptySuccessCandidate()
						&& failed.getCurrentEmptySuccessCount() >= emptyPageSkipThreshold) {
					if (!skipEmpty(jobId, pageNo, failed, incomplete)) return; continue;
				}
				if (!autoResume(jobId, pageNo, failure)) return;
			}
		}
	}

	private boolean skipEmpty(long jobId, int pageNo, ParkingSyncJob job, IncompletePublicDataPageException incomplete) {
		pageService.skipEmptyPage(jobId, pageNo, job.getCurrentEmptySuccessCount(), incomplete.totalCount(), incomplete.totalPages());
		ParkingSyncJob after = jobService.required(jobId);
		if (pageNo >= incomplete.totalPages()) { jobService.markCompleted(jobId); return false; }
		if (after.getConsecutiveEmptyPageCount() >= trailingEmptyPageThreshold) { completeTrailing(jobId); return false; }
		if (pauseRequested.get() || after.reachedPageLimit()) { pauseJob(jobId); return false; }
		return delay.await(pageDelayMs, pauseRequested::get) || pauseAndStop(jobId);
	}

	private boolean autoResume(long jobId, int pageNo, PublicDataFailure failure) {
		ParkingSyncJob job = jobService.required(jobId);
		if (!autoResumeEnabled || !failure.transientFailure() || job.getAutoResumeAttempts() >= maxAutoResumeAttempts) {
			jobService.markFailed(jobId, pageNo, failure); return false;
		}
		if (!delay.await(autoResumeDelayMs, pauseRequested::get) || pauseRequested.get()) { pauseJob(jobId); return false; }
		jobService.markAutoResume(jobId); return true;
	}

	private boolean pauseAndStop(long jobId) { pauseJob(jobId); return false; }
	private void pauseJob(long jobId) { jobService.markPaused(jobId); }
	private void completeTrailing(long jobId) { jobService.markCompletedForTrailingEmptyPages(jobId); }
	private void acquire() { if (!running.compareAndSet(false, true)) throw new FullSyncAlreadyRunningException(); }
	private ParkingOperationFullSyncStatus snapshot(long id) { return view(jobService.required(id)); }
	private ParkingOperationFullSyncStatus view(ParkingSyncJob job) {
		var pages = skippedPageRepository.findTop10ByJobIdOrderByCreatedAtDesc(job.getId()).stream()
				.map(ParkingSyncSkippedPageSummary::from).toList();
		return ParkingOperationFullSyncStatus.from(job, maxAutoResumeAttempts, emptyPageSkipThreshold,
				trailingEmptyPageThreshold, running.get() && activeJobId.get() == job.getId(), pages);
	}
	private long calculateTotalPages(long total, int rows) { return total == 0 ? 0 : ((total - 1) / rows) + 1; }
	private long parseTotalCount(String value) {
		try { long total = Long.parseLong(value); if (total < 0) throw new NumberFormatException(); return total; }
		catch (NumberFormatException | NullPointerException exception) { throw new IllegalStateException("Operation totalCount is missing or invalid"); }
	}
	private void validateCompleteness(int pageNo, long totalPages, long totalCount, int expected,
			PublicParkingOperationResponse response) {
		int actual = response.safeItems().size(); boolean success = "0".equals(response.resultCode()) && "SUCCESS".equalsIgnoreCase(response.resultMsg());
		if (actual == 0 && totalCount > 0) throw new IncompletePublicDataPageException(pageNo, 0, expected, success, totalCount, totalPages);
		if (pageNo < totalPages && actual < expected) throw new IncompletePublicDataPageException(pageNo, actual, expected, false, totalCount, totalPages);
	}
	private Integer validateExecution(Integer maxPages, int startPage, boolean full, String confirmation) {
		if (startPage < 1) throw new IllegalArgumentException("startPage must be at least 1");
		if (full) { if (!FULL_CONFIRMATION.equals(confirmation)) throw new IllegalArgumentException("Unlimited operation sync requires explicit confirmation"); return null; }
		if (maxPages == null || maxPages < 1 || maxPages > MAX_LIMITED_PAGES) throw new IllegalArgumentException("maxPages is required and must be between 1 and 100");
		return maxPages;
	}
	private long elapsed(long nanos) { return Duration.ofNanos(System.nanoTime() - nanos).toMillis(); }
}
