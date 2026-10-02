package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

class PublicParkingFacilityFullSyncServiceTest {
	@Test
	void nineConsecutiveSkippedEmptyPagesDoNotFinishTrailingEarly() {
		Fixture fixture = fixture(9, true, 5, 1);
		when(fixture.client.fetchFacilities(anyInt(), anyInt())).thenAnswer(invocation ->
				response(invocation.getArgument(0), "100", items(0)));

		fixture.service.start(9, 1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		assertEquals(9, fixture.job.getConsecutiveEmptyPageCount());
		assertEquals(null, fixture.job.getCompletionReason());
		assertEquals(10, fixture.job.getNextPage());
	}

	@Test
	void tenConsecutiveSkippedEmptyPagesCompleteWithProviderMismatchReason() {
		Fixture fixture = fixture(10, true, 5, 1);
		when(fixture.client.fetchFacilities(anyInt(), anyInt())).thenAnswer(invocation ->
				response(invocation.getArgument(0), "100", items(0)));

		fixture.service.start(10, 1, false, null);
		fixture.executor.runSubmitted();

		ParkingFacilityFullSyncStatus status = fixture.service.status(1L);
		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, status.status());
		assertEquals(ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES,
				status.completionReason());
		assertEquals(10, status.consecutiveEmptyPageCount());
		assertEquals(1, status.firstTrailingEmptyPage());
		assertEquals(10, status.lastProcessedPage());
		assertFalse(status.workerRunning());
		verify(fixture.client, never()).fetchFacilities(11, 1);
	}

	@Test
	void nonEmptyPageResetsTrailingEmptyRun() {
		Fixture fixture = fixture(6, true, 5, 1);
		when(fixture.client.fetchFacilities(anyInt(), anyInt())).thenAnswer(invocation -> {
			int pageNo = invocation.getArgument(0);
			return pageNo <= 5 ? response(pageNo, "100", items(0))
					: response(pageNo, "100", item("DATA"));
		});

		fixture.service.start(6, 1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		assertEquals(0, fixture.job.getConsecutiveEmptyPageCount());
		assertEquals(6, fixture.job.getLastDataPage());
		assertEquals(null, fixture.job.getFirstTrailingEmptyPage());
	}

	@Test
	void timeoutAndPartialPageNeverIncreaseTrailingEmptyPageCount() {
		Fixture timeoutFixture = fixture(2, true, 5, 1);
		when(timeoutFixture.client.fetchFacilities(1, 1)).thenReturn(response(1, "100", items(0)));
		when(timeoutFixture.client.fetchFacilities(2, 1)).thenThrow(timeout());
		timeoutFixture.service.start(2, 1, false, null);
		timeoutFixture.executor.runSubmitted();
		assertEquals(1, timeoutFixture.job.getConsecutiveEmptyPageCount());
		assertEquals(ParkingSyncJobStatus.FAILED, timeoutFixture.job.getStatus());

		Fixture partialFixture = fixture(2, true, 5, 2);
		when(partialFixture.client.fetchFacilities(1, 2)).thenReturn(response(1, "200", items(0)));
		when(partialFixture.client.fetchFacilities(2, 2)).thenReturn(response(2, "200", item("SHORT")));
		partialFixture.service.start(2, 1, false, null);
		partialFixture.executor.runSubmitted();
		assertEquals(1, partialFixture.job.getConsecutiveEmptyPageCount());
		assertEquals(ParkingSyncJobStatus.FAILED, partialFixture.job.getStatus());
	}

	@Test
	void repeatedConfirmedEmptyPageIsRecordedAndCheckpointAdvances() {
		Fixture fixture = fixture(1, true, 2, 1000);
		position(fixture.job, 1510, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1511, 1000))
				.thenReturn(response(1511, "1767934", items(0)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		assertEquals(1511, fixture.job.getLastSuccessPage());
		assertEquals(1512, fixture.job.getNextPage());
		assertEquals(1, fixture.job.getSkippedPageCount());
		verify(fixture.client, times(3)).fetchFacilities(1511, 1000);
		verify(fixture.pages).skipEmptyPage(1L, 1511, 3, 1_767_934, 1768);
	}

	@Test
	void emptyObservationsAccumulateAcrossNetworkFailures() {
		Fixture fixture = fixture(1, true, 5, 1000);
		position(fixture.job, 1512, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1513, 1000))
				.thenReturn(response(1513, "1767934", items(0)))
				.thenThrow(timeout())
				.thenReturn(response(1513, "1767934", items(0)))
				.thenThrow(timeout())
				.thenReturn(response(1513, "1767934", items(0)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		assertEquals(1514, fixture.job.getNextPage());
		assertEquals(0, fixture.job.getCurrentEmptySuccessCount());
		verify(fixture.pages).skipEmptyPage(1L, 1513, 3, 1_767_934, 1768);
	}

	@Test
	void persistedEmptyObservationSurvivesExplicitResumeCycle() {
		ParkingSyncJob job = ParkingSyncJob.facilityFull(1513, 1000, null);
		job.recordEmptySuccessFailure(1513, transientFailure());
		job.recordPageFailure(1513, transientFailure());
		job.recordEmptySuccessFailure(1513, transientFailure());
		job.markFailed(1513, transientFailure(), "safe");

		job.prepareExplicitResume(1);

		assertEquals(1513, job.getCurrentEmptyPageNo());
		assertEquals(2, job.getCurrentEmptySuccessCount());
	}

	@Test
	void normalFullPageResetsPreviousEmptyObservation() {
		Fixture fixture = fixture(1, true, 5, 1000);
		position(fixture.job, 1512, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1513, 1000))
				.thenReturn(response(1513, "1767934", items(0)))
				.thenThrow(timeout())
				.thenReturn(response(1513, "1767934", items(1000)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(1514, fixture.job.getNextPage());
		assertEquals(0, fixture.job.getCurrentEmptySuccessCount());
		assertEquals(null, fixture.job.getCurrentEmptyPageNo());
	}

	@Test
	void partialPageDoesNotCountAsEmptyAndClearsEmptyEvidence() {
		Fixture fixture = fixture(1, true, 1, 1000);
		position(fixture.job, 1512, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1513, 1000))
				.thenReturn(response(1513, "1767934", items(0)))
				.thenReturn(response(1513, "1767934", items(300)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.FAILED, fixture.job.getStatus());
		assertEquals(0, fixture.job.getCurrentEmptySuccessCount());
		assertEquals(1513, fixture.job.getNextPage());
	}

	@Test
	void repeatedPartialPageFailsWithoutSavingOrAdvancingCheckpoint() {
		Fixture fixture = fixture(1, true, 2, 1000);
		position(fixture.job, 1510, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1511, 1000))
				.thenReturn(response(1511, "1767934", items(300)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.FAILED, fixture.job.getStatus());
		assertEquals(1510, fixture.job.getLastSuccessPage());
		assertEquals(1511, fixture.job.getNextPage());
		assertEquals(0, fixture.job.getSkippedPageCount());
		verify(fixture.client, times(3)).fetchFacilities(1511, 1000);
	}

	@Test
	void timeoutIsNeverSkippedAfterRetryBudgetIsExhausted() {
		Fixture fixture = fixture(1, true, 2, 1000);
		position(fixture.job, 1510, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1511, 1000)).thenThrow(timeout());

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.FAILED, fixture.job.getStatus());
		assertEquals(1511, fixture.job.getNextPage());
		assertEquals(0, fixture.job.getSkippedPageCount());
	}

	@Test
	void emptyResponseWithoutSuccessMessageIsNotSkippable() {
		Fixture fixture = fixture(1, true, 1, 1000);
		position(fixture.job, 1510, 1768, 1_767_934);
		fixture.job.markPaused();
		PublicParkingFacilityResponse notConfirmed = new PublicParkingFacilityResponse(
				List.of(), "1511", "0", "1767934", "1000", "TEMPORARY");
		when(fixture.client.fetchFacilities(1511, 1000)).thenReturn(notConfirmed);

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.FAILED, fixture.job.getStatus());
		assertEquals(1511, fixture.job.getNextPage());
		assertEquals(0, fixture.job.getSkippedPageCount());
	}

	@Test
	void finalPageMayBeShortAndCompletesAtOneHundredPercent() {
		Fixture fixture = fixture(1, false, 5, 1000);
		position(fixture.job, 1767, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1768, 1000))
				.thenReturn(response(1768, "1767934", items(300)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		ParkingFacilityFullSyncStatus status = fixture.service.status();
		assertEquals(ParkingSyncJobStatus.COMPLETED, status.status());
		assertEquals(1768, status.lastSuccessPage());
		assertEquals(new java.math.BigDecimal("100.00"), status.progressPercent());
	}

	@Test
	void finalEmptyPageCompletesWithGapsOnlyAfterRetryBudget() {
		Fixture fixture = fixture(1, true, 2, 1000);
		position(fixture.job, 1767, 1768, 1_767_934);
		fixture.job.markPaused();
		when(fixture.client.fetchFacilities(1768, 1000))
				.thenReturn(response(1768, "1767934", items(0)));

		fixture.service.resume(1, false, null);
		fixture.executor.runSubmitted();

		ParkingFacilityFullSyncStatus status = fixture.service.status();
		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, status.status());
		assertEquals(1768, status.lastSuccessPage());
		assertEquals(1, status.skippedPageCount());
		assertEquals(true, status.hasDataGaps());
		assertEquals(new java.math.BigDecimal("100.00"), status.progressPercent());
	}

	@Test
	void completionInvariantRejectsPrematureStateAndRepairPreservesCheckpoint() {
		Fixture fixture = fixture(1, false, 5, 1000);
		position(fixture.job, 1510, 1768, 1_767_934);
		assertThrows(IllegalStateException.class, fixture.job::markCompleted);

		ReflectionTestUtils.setField(fixture.job, "status", ParkingSyncJobStatus.COMPLETED);
		ParkingFacilityFullSyncStatus repaired = fixture.service.repairPrematureCompletion(1L);
		assertEquals(ParkingSyncJobStatus.PAUSED, repaired.status());
		assertEquals(1510, repaired.lastSuccessPage());
		assertEquals(1511, repaired.nextPage());
	}

	@Test
	void newJobUsesConfiguredThousandRowsWhileExistingJobKeepsStoredHundredRows() {
		Fixture newJob = fixture(1, true, 3, 1000);
		when(newJob.client.fetchFacilities(1, 1000)).thenReturn(response(1, "1", item("A")));

		newJob.service.start(1, 1, false, null);
		newJob.executor.runSubmitted();

		assertEquals(1000, newJob.job.getNumOfRows());
		verify(newJob.jobs).create(1, 1000, 1);
		verify(newJob.client).fetchFacilities(1, 1000);
		assertEquals(1768, newJob.service.calculateTotalPages(1_767_934, 1000));
	}

	@Test
	void startSubmitsBackgroundWorkerAndReturnsBeforeCallingClient() {
		Fixture fixture = fixture(3, true, 3);
		when(fixture.client.fetchFacilities(1, 1)).thenReturn(response(1, "3", item("A")));

		ParkingFacilityFullSyncStatus accepted = fixture.service.start(3, 1, false, null);

		assertEquals(ParkingSyncJobStatus.RUNNING, accepted.status());
		assertEquals(1, accepted.nextPage());
		verify(fixture.client, never()).fetchFacilities(anyInt(), anyInt());
		fixture.executor.runSubmitted();
		verify(fixture.client).fetchFacilities(1, 1);
	}

	@Test
	void autoResumesFailedCheckpointAndResetsFailureCountersAfterSuccess() {
		Fixture fixture = fixture(1, true, 3);
		when(fixture.client.fetchFacilities(1, 1))
				.thenThrow(timeout())
				.thenReturn(response(1, "1", item("A")));

		fixture.service.start(1, 1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.COMPLETED, fixture.job.getStatus());
		assertEquals(1, fixture.job.getLastSuccessPage());
		assertEquals(0, fixture.job.getAutoResumeAttempts());
		assertEquals(0, fixture.job.getConsecutiveFailureCount());
		assertNotNull(fixture.job.getLastFailureAt());
		assertNotNull(fixture.job.getLastResumeAt());
		verify(fixture.client, times(2)).fetchFacilities(1, 1);
	}

	@Test
	void stopsAfterConfiguredAutoResumeAttempts() {
		Fixture fixture = fixture(1, true, 5);
		when(fixture.client.fetchFacilities(1, 1)).thenThrow(timeout());

		fixture.service.start(1, 1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.FAILED, fixture.job.getStatus());
		assertEquals(1, fixture.job.getNextPage());
		assertEquals(5, fixture.job.getAutoResumeAttempts());
		assertEquals(6, fixture.job.getConsecutiveFailureCount());
		ParkingFacilityFullSyncStatus status = fixture.service.status();
		assertEquals(PublicDataFailureCategory.TRANSIENT_NETWORK, status.failureCategory());
		assertEquals("HttpTimeoutException", status.rootErrorType());
		assertEquals(0, status.currentRetryBudget());
		verify(fixture.client, times(6)).fetchFacilities(1, 1);
	}

	@Test
	void maxPagesDoesNotResetDuringAutoResume() {
		Fixture fixture = fixture(2, true, 3);
		when(fixture.client.fetchFacilities(1, 1)).thenReturn(response(1, "3", item("A")));
		when(fixture.client.fetchFacilities(2, 1))
				.thenThrow(timeout())
				.thenReturn(response(2, "300", item("B")));

		fixture.service.start(2, 1, false, null);
		fixture.executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		assertEquals(2, fixture.job.getSuccessfulPages());
		assertEquals(3, fixture.job.getNextPage());
		verify(fixture.client, never()).fetchFacilities(3, 1);
	}

	@Test
	void pauseDuringAutoResumeWaitPreventsAnotherCall() {
		CapturingTaskExecutor executor = new CapturingTaskExecutor();
		FullSyncDelay delay = mock(FullSyncDelay.class);
		Fixture fixture = fixture(3, true, 3, executor, delay);
		when(fixture.client.fetchFacilities(1, 1)).thenThrow(timeout());
		when(delay.await(anyLong(), any())).thenAnswer(invocation -> {
			fixture.service.requestPause();
			return false;
		});

		fixture.service.start(3, 1, false, null);
		executor.runSubmitted();

		assertEquals(ParkingSyncJobStatus.PAUSED, fixture.job.getStatus());
		verify(fixture.client).fetchFacilities(1, 1);
	}

	@Test
	void explicitResumeStartsAtCheckpointAndStaleJobDoesNotStartAutomatically() {
		Fixture fixture = fixture(4, false, 0, 100);
		fixture.job.markRunning();
		fixture.job.pageSucceeded(1, 200, 2, 100, new ParkingFacilityUpsertResult(100, 0), 0, 0);
		fixture.job.recordPageFailure(2, transientFailure());
		fixture.job.markAutoResume();
		fixture.job.recordPageFailure(2, transientFailure());
		fixture.job.markFailed(2, transientFailure(), "safe");
		when(fixture.client.fetchFacilities(2, 100)).thenReturn(response(2, "200", item("B")));

		assertFalse(fixture.executor.hasSubmittedTask());
		assertFalse(fixture.service.status(1L).workerRunning());
		fixture.service.resume(1, false, null);
		assertEquals(true, fixture.service.status(1L).workerRunning());
		assertFalse(fixture.job.getStatus() == ParkingSyncJobStatus.FAILED);
		assertEquals(0, fixture.job.getAutoResumeAttempts());
		assertEquals(0, fixture.job.getConsecutiveFailureCount());
		assertNotNull(fixture.job.getLastFailureAt());
		fixture.executor.runSubmitted();
		verify(fixture.client).fetchFacilities(2, 100);
		assertEquals(100, fixture.job.getNumOfRows());
		assertEquals(3, fixture.job.getNextPage());
	}

	@Test
	void blocksDuplicateWorkerSubmissionAndValidatesLimits() {
		Fixture fixture = fixture(3, true, 3);
		fixture.service.start(3, 1, false, null);
		assertThrows(FullSyncAlreadyRunningException.class,
				() -> fixture.service.start(3, 1, false, null));
		assertThrows(FullSyncAlreadyRunningException.class,
				() -> fixture.service.resume(3, false, null));
		assertThrows(IllegalArgumentException.class,
				() -> fixture(3, true, 3).service.start(null, 1, false, null));
		assertEquals(17680, fixture.service.calculateTotalPages(1_767_934, 100));
	}

	@Test
	void terminalWorkerAlwaysReleasesGlobalLock() {
		Fixture fixture = fixture(1, true, 1);
		when(fixture.client.fetchFacilities(1, 1)).thenReturn(response(1, "1", item("A")));

		fixture.service.start(1, 1, false, null);
		fixture.executor.runSubmitted();

		assertFalse(fixture.service.status(1L).workerRunning());
		fixture.job.markPaused();
		fixture.service.resume(1, false, null);
	}

	@Test
	void latestStatusAndSpecificJobStatusAreDistinct() {
		ParkingSyncJob job9 = ParkingSyncJob.facilityFull(1, 1000, null);
		ParkingSyncJob job14 = ParkingSyncJob.facilityFull(1, 1, 3);
		ReflectionTestUtils.setField(job9, "id", 9L);
		ReflectionTestUtils.setField(job14, "id", 14L);
		ParkingSyncJobService jobs = mock(ParkingSyncJobService.class);
		when(jobs.latest()).thenReturn(job14);
		when(jobs.required(9L)).thenReturn(job9);
		ParkingSyncSkippedPageRepository skippedPages = mock(ParkingSyncSkippedPageRepository.class);
		when(skippedPages.findTop10ByJobIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
		PublicParkingFacilityFullSyncService service = new PublicParkingFacilityFullSyncService(
				mock(PublicParkingClient.class), mock(PublicParkingFacilityFullPageService.class), jobs,
				skippedPages, new CapturingTaskExecutor(), immediateDelay(), 1000, 0, true, 0, 5, 3, 10);

		assertEquals(14L, service.status().jobId());
		assertEquals(9L, service.status(9L).jobId());
		assertFalse(service.status(9L).workerRunning());
	}

	private Fixture fixture(Integer pageLimit, boolean autoResume, int maxAttempts) {
		return fixture(pageLimit, autoResume, maxAttempts, 1);
	}

	private Fixture fixture(Integer pageLimit, boolean autoResume, int maxAttempts, int numOfRows) {
		return fixture(pageLimit, autoResume, maxAttempts, numOfRows,
				new CapturingTaskExecutor(), immediateDelay());
	}

	private Fixture fixture(Integer pageLimit, boolean autoResume, int maxAttempts,
			CapturingTaskExecutor executor, FullSyncDelay delay) {
		return fixture(pageLimit, autoResume, maxAttempts, 1, executor, delay);
	}

	private Fixture fixture(Integer pageLimit, boolean autoResume, int maxAttempts, int numOfRows,
			CapturingTaskExecutor executor, FullSyncDelay delay) {
		PublicParkingClient client = mock(PublicParkingClient.class);
		ParkingSyncJob job = ParkingSyncJob.facilityFull(1, numOfRows, pageLimit);
		ReflectionTestUtils.setField(job, "id", 1L);
		ParkingSyncJobService jobs = jobService(job);
		PublicParkingFacilityFullPageService pages = pageService(job);
		ParkingSyncSkippedPageRepository skippedPages = mock(ParkingSyncSkippedPageRepository.class);
		when(skippedPages.findTop10ByJobIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
		PublicParkingFacilityFullSyncService service = new PublicParkingFacilityFullSyncService(
				client, pages, jobs, skippedPages, executor, delay,
				numOfRows, 0, autoResume, 0, maxAttempts, 3, 10);
		return new Fixture(client, job, service, jobs, pages, executor);
	}

	private FullSyncDelay immediateDelay() {
		FullSyncDelay delay = mock(FullSyncDelay.class);
		when(delay.await(anyLong(), any())).thenReturn(true);
		return delay;
	}

	private ParkingSyncJobService jobService(ParkingSyncJob job) {
		ParkingSyncJobService jobs = mock(ParkingSyncJobService.class);
		when(jobs.create(anyInt(), anyInt(), nullable(Integer.class))).thenReturn(job);
		when(jobs.required(anyLong())).thenReturn(job);
		when(jobs.latest()).thenReturn(job);
		when(jobs.latestResumable()).thenReturn(job);
		when(jobs.requiredResumable(anyLong())).thenReturn(job);
		doAnswer(i -> { job.markRunning(); return null; }).when(jobs).markRunning(anyLong());
		doAnswer(i -> { job.prepareExplicitResume(i.getArgument(1)); return null; })
				.when(jobs).prepareExplicitResume(anyLong(), nullable(Integer.class));
		doAnswer(i -> { job.markPageStarted(i.getArgument(1)); return null; }).when(jobs).markPageStarted(anyLong(), anyInt());
		doAnswer(i -> { job.recordPageFailure(i.getArgument(1), i.getArgument(2)); return null; })
				.when(jobs).recordPageFailure(anyLong(), anyInt(), any(PublicDataFailure.class));
		doAnswer(i -> { job.recordEmptySuccessFailure(i.getArgument(1), i.getArgument(2)); return null; })
				.when(jobs).recordEmptySuccessFailure(anyLong(), anyInt(), any(PublicDataFailure.class));
		doAnswer(i -> { job.recordPartialPageFailure(i.getArgument(1), i.getArgument(2)); return null; })
				.when(jobs).recordPartialPageFailure(anyLong(), anyInt(), any(PublicDataFailure.class));
		doAnswer(i -> { job.markAutoResume(); return null; }).when(jobs).markAutoResume(anyLong());
		doAnswer(i -> { job.markPaused(); return null; }).when(jobs).markPaused(anyLong());
		doAnswer(i -> { job.markCompleted(); return null; }).when(jobs).markCompleted(anyLong());
		doAnswer(i -> { job.markCompletedForTrailingEmptyPages(); return null; })
				.when(jobs).markCompletedForTrailingEmptyPages(anyLong());
		doAnswer(i -> { job.repairPrematureCompletion(); return null; })
				.when(jobs).repairPrematureCompletion(anyLong());
		doAnswer(i -> { job.markFailed(i.getArgument(1), i.getArgument(2), "safe"); return null; })
				.when(jobs).markFailed(anyLong(), anyInt(), any(PublicDataFailure.class));
		return jobs;
	}

	private PublicParkingFacilityFullPageService pageService(ParkingSyncJob job) {
		PublicParkingFacilityFullPageService pages = mock(PublicParkingFacilityFullPageService.class);
		when(pages.process(anyLong(), anyInt(), any(), anyLong(), anyLong())).thenAnswer(i -> {
			int pageNo = i.getArgument(1);
			PublicParkingFacilityResponse response = i.getArgument(2);
			ParkingFacilityUpsertResult upsert = new ParkingFacilityUpsertResult(response.safeItems().size(), 0);
			job.pageSucceeded(pageNo, i.getArgument(3), i.getArgument(4), response.safeItems().size(), upsert, 0, 0);
			return new ParkingFacilityFullPageResult(response.safeItems().size(), upsert.insertedCount(), 0,
					0, 0, response.safeItems().isEmpty());
		});
		doAnswer(i -> {
			job.emptyPageSkipped(i.getArgument(1), i.getArgument(3), i.getArgument(4));
			return null;
		}).when(pages).skipEmptyPage(anyLong(), anyInt(), anyInt(), anyLong(), anyLong());
		return pages;
	}

	private PublicParkingFacilityResponse response(int pageNo, String totalCount, PublicParkingFacilityItem... items) {
		return new PublicParkingFacilityResponse(List.of(items), Integer.toString(pageNo), "0", totalCount, "100", "SUCCESS");
	}

	private PublicParkingFacilityItem[] items(int count) {
		return IntStream.range(0, count).mapToObj(index -> item("item-" + index))
				.toArray(PublicParkingFacilityItem[]::new);
	}

	private void position(ParkingSyncJob job, int lastSuccessPage, long totalPages, long totalCount) {
		ReflectionTestUtils.setField(job, "lastSuccessPage", lastSuccessPage);
		ReflectionTestUtils.setField(job, "nextPage", lastSuccessPage + 1);
		ReflectionTestUtils.setField(job, "totalPages", totalPages);
		ReflectionTestUtils.setField(job, "totalCount", totalCount);
	}

	private PublicParkingFacilityItem item(String id) {
		return new PublicParkingFacilityItem(id, "name", "address", "sido", "sigungu", "37.1", "127.1", "10");
	}

	private ResourceAccessException timeout() {
		return new ResourceAccessException("timeout", new HttpTimeoutException("timed out"));
	}

	private PublicDataFailure transientFailure() {
		return new PublicDataFailure(PublicDataFailureCategory.TRANSIENT_NETWORK, "HttpTimeoutException");
	}

	private record Fixture(PublicParkingClient client, ParkingSyncJob job,
			PublicParkingFacilityFullSyncService service, ParkingSyncJobService jobs,
			PublicParkingFacilityFullPageService pages, CapturingTaskExecutor executor) {}

	private static class CapturingTaskExecutor implements TaskExecutor {
		private Runnable submitted;
		@Override public void execute(Runnable task) { submitted = task; }
		void runSubmitted() { Runnable task = submitted; submitted = null; task.run(); }
		boolean hasSubmittedTask() { return submitted != null; }
	}
}
