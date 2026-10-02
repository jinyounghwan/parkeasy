package com.parkeasy.backend.external.parking;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class PublicParkingRealtimeFullSyncService {
	public static final String FULL_CONFIRMATION = "REALTIME_FULL_SYNC";
	private static final int MAX_LIMITED_PAGES = 100;
	private final PublicParkingRealtimeClient client;
	private final PublicParkingRealtimeFullPageService pageService;
	private final ParkingSyncJobService jobs;
	private final ParkingSyncSkippedPageRepository skippedPages;
	private final TaskExecutor executor;
	private final FullSyncDelay delay;
	private final int numOfRows, maxAutoResumeAttempts, emptyThreshold, trailingThreshold;
	private final long pageDelayMs, autoResumeDelayMs;
	private final boolean autoResumeEnabled;
	private final AtomicBoolean running = new AtomicBoolean(), pauseRequested = new AtomicBoolean();
	private final AtomicLong activeJobId = new AtomicLong();
	private final PublicDataFailureClassifier classifier = new PublicDataFailureClassifier();

	public PublicParkingRealtimeFullSyncService(PublicParkingRealtimeClient client,
			PublicParkingRealtimeFullPageService pageService, ParkingSyncJobService jobs,
			ParkingSyncSkippedPageRepository skippedPages,
			@Qualifier("parkingRealtimeFullSyncTaskExecutor") TaskExecutor executor, FullSyncDelay delay,
			@Value("${parkeasy.public-data.full-sync.realtime.num-of-rows:1000}") int numOfRows,
			@Value("${parkeasy.public-data.full-sync.realtime.page-delay-ms:1000}") long pageDelayMs,
			@Value("${parkeasy.public-data.full-sync.realtime.auto-resume-enabled:true}") boolean autoResumeEnabled,
			@Value("${parkeasy.public-data.full-sync.realtime.auto-resume-delay-ms:60000}") long autoResumeDelayMs,
			@Value("${parkeasy.public-data.full-sync.realtime.max-auto-resume-attempts:5}") int maxAutoResumeAttempts,
			@Value("${parkeasy.public-data.full-sync.realtime.empty-page-skip-threshold:3}") int emptyThreshold,
			@Value("${parkeasy.public-data.full-sync.realtime.trailing-empty-page-threshold:10}") int trailingThreshold) {
		if (numOfRows < 1 || numOfRows > 1000) throw new IllegalArgumentException("Realtime numOfRows must be between 1 and 1000");
		if (pageDelayMs < 0 || autoResumeDelayMs < 0) throw new IllegalArgumentException("Realtime delays must not be negative");
		if (maxAutoResumeAttempts < 0 || maxAutoResumeAttempts > 100
				|| emptyThreshold < 1 || emptyThreshold > 100 || trailingThreshold < 1 || trailingThreshold > 100) {
			throw new IllegalArgumentException("Realtime retry and empty thresholds are out of range");
		}
		this.client = client; this.pageService = pageService; this.jobs = jobs; this.skippedPages = skippedPages;
		this.executor = executor; this.delay = delay; this.numOfRows = numOfRows; this.pageDelayMs = pageDelayMs;
		this.autoResumeEnabled = autoResumeEnabled; this.autoResumeDelayMs = autoResumeDelayMs;
		this.maxAutoResumeAttempts = maxAutoResumeAttempts; this.emptyThreshold = emptyThreshold;
		this.trailingThreshold = trailingThreshold;
	}

	public ParkingRealtimeFullSyncStatus start(Integer maxPages, int startPage, boolean full, String confirm) {
		Integer limit = validate(maxPages, startPage, full, confirm); acquire();
		try {
			ParkingSyncJob job = jobs.createRealtime(startPage, numOfRows, limit); jobs.markRunning(job.getId());
			ParkingRealtimeFullSyncStatus accepted = snapshot(job.getId()); submit(job.getId()); return accepted;
		} catch (RuntimeException exception) { running.set(false); throw exception; }
	}

	public ParkingRealtimeFullSyncStatus resume(Long jobId, Integer maxPages, boolean full, String confirm) {
		Integer limit = validate(maxPages, 1, full, confirm); acquire();
		try {
			ParkingSyncJob job = jobId == null ? jobs.latestRealtimeResumable() : jobs.requiredRealtimeResumable(jobId);
			jobs.prepareExplicitResume(job.getId(), limit); pageService.bootstrapTrailingEmptyPages(job.getId());
			ParkingRealtimeFullSyncStatus accepted = snapshot(job.getId()); submit(job.getId()); return accepted;
		} catch (RuntimeException exception) { running.set(false); throw exception; }
	}

	public ParkingRealtimeFullSyncStatus pause() {
		if (running.get()) pauseRequested.set(true);
		long id = activeJobId.get(); return id == 0 ? status(null) : status(id);
	}

	public ParkingRealtimeFullSyncStatus status(Long jobId) {
		ParkingSyncJob job = jobId == null ? jobs.latestRealtime() : jobs.required(jobId);
		if (!ParkingSyncJob.REALTIME_FULL.equals(job.getSyncType())) throw new IllegalArgumentException("Sync job is not a realtime full sync job");
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
			ParkingSyncJob job = jobs.required(jobId); jobs.markFailed(jobId, job.getNextPage(), classifier.classify(exception));
		} finally { activeJobId.compareAndSet(jobId, 0); running.set(false); }
	}

	private void runPages(long jobId) {
		while (true) {
			ParkingSyncJob before = jobs.required(jobId);
			if (before.getConsecutiveEmptyPageCount() >= trailingThreshold) { jobs.markCompletedForTrailingEmptyPages(jobId); return; }
			if (pauseRequested.get()) { jobs.markPaused(jobId); return; }
			int pageNo = before.getNextPage(); jobs.markPageStarted(jobId, pageNo);
			try {
				PublicParkingRealtimeResponse response = client.fetchRealtime(pageNo, before.getNumOfRows());
				long totalCount = before.getTotalCount() == null ? parseTotal(response.totalCount()) : before.getTotalCount();
				long totalPages = totalCount == 0 ? 0 : ((totalCount - 1) / before.getNumOfRows()) + 1;
				validatePage(pageNo, totalPages, totalCount, before.getNumOfRows(), response);
				pageService.process(jobId, pageNo, response, totalCount, totalPages);
				ParkingSyncJob after = jobs.required(jobId);
				if (pageNo >= totalPages) { jobs.markCompleted(jobId); return; }
				if (pauseRequested.get() || after.reachedPageLimit()) { jobs.markPaused(jobId); return; }
				if (!delay.await(pageDelayMs, pauseRequested::get)) { jobs.markPaused(jobId); return; }
			} catch (RuntimeException exception) {
				PublicDataFailure failure = classifier.classify(exception);
				IncompletePublicDataPageException incomplete = exception instanceof IncompletePublicDataPageException value ? value : null;
				if (incomplete != null && incomplete.emptySuccessCandidate()) jobs.recordEmptySuccessFailure(jobId, pageNo, failure);
				else if (incomplete != null && incomplete.actualRows() > 0) jobs.recordPartialPageFailure(jobId, pageNo, failure);
				else jobs.recordPageFailure(jobId, pageNo, failure);
				ParkingSyncJob failed = jobs.required(jobId);
				if (incomplete != null && incomplete.emptySuccessCandidate()
						&& failed.getCurrentEmptySuccessCount() >= emptyThreshold) {
					if (!skipEmpty(jobId, pageNo, failed, incomplete)) return; continue;
				}
				if (!autoResume(jobId, pageNo, failure)) return;
			}
		}
	}

	private boolean skipEmpty(long jobId, int pageNo, ParkingSyncJob job, IncompletePublicDataPageException incomplete) {
		pageService.skipEmptyPage(jobId, pageNo, job.getCurrentEmptySuccessCount(), incomplete.totalCount(), incomplete.totalPages());
		ParkingSyncJob after = jobs.required(jobId);
		if (pageNo >= incomplete.totalPages()) { jobs.markCompleted(jobId); return false; }
		if (after.getConsecutiveEmptyPageCount() >= trailingThreshold) { jobs.markCompletedForTrailingEmptyPages(jobId); return false; }
		if (pauseRequested.get() || after.reachedPageLimit()) { jobs.markPaused(jobId); return false; }
		if (!delay.await(pageDelayMs, pauseRequested::get)) { jobs.markPaused(jobId); return false; }
		return true;
	}

	private boolean autoResume(long jobId, int pageNo, PublicDataFailure failure) {
		ParkingSyncJob job = jobs.required(jobId);
		if (!autoResumeEnabled || !failure.transientFailure() || job.getAutoResumeAttempts() >= maxAutoResumeAttempts) {
			jobs.markFailed(jobId, pageNo, failure); return false;
		}
		if (!delay.await(autoResumeDelayMs, pauseRequested::get) || pauseRequested.get()) { jobs.markPaused(jobId); return false; }
		jobs.markAutoResume(jobId); return true;
	}

	private void validatePage(int pageNo, long totalPages, long totalCount, int expected,
			PublicParkingRealtimeResponse response) {
		int actual = response.safeItems().size(); boolean success = "0".equals(response.resultCode()) && "SUCCESS".equalsIgnoreCase(response.resultMsg());
		if (actual == 0 && totalCount > 0) throw new IncompletePublicDataPageException(pageNo, 0, expected, success, totalCount, totalPages);
		if (pageNo < totalPages && actual < expected) throw new IncompletePublicDataPageException(pageNo, actual, expected, false, totalCount, totalPages);
	}

	private long parseTotal(String value) {
		try { long result = Long.parseLong(value); if (result < 0) throw new NumberFormatException(); return result; }
		catch (NumberFormatException | NullPointerException exception) { throw new IllegalStateException("Realtime totalCount is missing or invalid"); }
	}
	private Integer validate(Integer maxPages, int startPage, boolean full, String confirm) {
		if (startPage < 1) throw new IllegalArgumentException("startPage must be at least 1");
		if (full) { if (!FULL_CONFIRMATION.equals(confirm)) throw new IllegalArgumentException("Unlimited realtime sync requires explicit confirmation"); return null; }
		if (maxPages == null || maxPages < 1 || maxPages > MAX_LIMITED_PAGES) throw new IllegalArgumentException("maxPages is required and must be between 1 and 100");
		return maxPages;
	}
	private void acquire() { if (!running.compareAndSet(false, true)) throw new FullSyncAlreadyRunningException(); }
	private ParkingRealtimeFullSyncStatus snapshot(long id) { return view(jobs.required(id)); }
	private ParkingRealtimeFullSyncStatus view(ParkingSyncJob job) {
		var recent = skippedPages.findTop10ByJobIdOrderByCreatedAtDesc(job.getId()).stream()
				.map(ParkingSyncSkippedPageSummary::from).toList();
		return ParkingRealtimeFullSyncStatus.from(job, maxAutoResumeAttempts, emptyThreshold,
				trailingThreshold, running.get() && activeJobId.get() == job.getId(), recent);
	}
}
