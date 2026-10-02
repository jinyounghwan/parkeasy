package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import jakarta.persistence.EntityManager;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;

@ActiveProfiles("full-sync-integration")
@SpringBootTest(properties = {
		"parkeasy.public-data.full-sync.facility.page-delay-ms=0",
		"parkeasy.public-data.full-sync.facility.num-of-rows=1",
		"parkeasy.public-data.full-sync.facility.auto-resume-delay-ms=1",
		"parkeasy.public-data.full-sync.facility.max-auto-resume-attempts=3" })
class PublicParkingFacilityFullSyncIntegrationTest {
	private static final List<String> IDS = List.of(
			"FULL-INTEGRATION-001", "FULL-INTEGRATION-002", "FULL-INTEGRATION-003");

	@Autowired private PublicParkingFacilityFullSyncService service;
	@Autowired private ParkingLotRepository parkingLotRepository;
	@Autowired private ParkingSyncJobRepository jobRepository;
	@Autowired private ParkingSyncSkippedPageRepository skippedPageRepository;
	@Autowired private ParkingSyncJobService jobService;
	@Autowired private EntityManager entityManager;
	@MockitoBean private PublicParkingClient client;
	private Long jobId;

	@AfterEach
	void cleanUp() {
		parkingLotRepository.deleteAll(parkingLotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, IDS));
		if (jobId != null) {
			skippedPageRepository.deleteByJobId(jobId);
			jobRepository.deleteById(jobId);
		}
	}

	@Test
	void preservesCommittedPageAndAutomaticallyResumesFailedPage() throws Exception {
		when(client.fetchFacilities(1, 1)).thenReturn(response(1, item(IDS.get(0))));
		when(client.fetchFacilities(2, 1)).thenThrow(
				new ResourceAccessException("simulated timeout", new HttpTimeoutException("timed out")))
				.thenReturn(response(2, item(IDS.get(1))));
		when(client.fetchFacilities(3, 1)).thenReturn(response(3, item(IDS.get(2))));

		ParkingFacilityFullSyncStatus accepted = service.start(3, 1, false, null);
		jobId = accepted.jobId();
		ParkingFacilityFullSyncStatus completed = awaitTerminalStatus();
		assertEquals(ParkingSyncJobStatus.COMPLETED, completed.status());
		assertEquals(3, completed.lastSuccessPage());
		assertEquals(0, completed.duplicateCount());
		assertEquals(3, parkingLotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, IDS).size());
	}

	@Test
	void skipsRepeatedSuccessfulEmptyPageAndCompletesWithRecordedGap() throws Exception {
		when(client.fetchFacilities(1, 1)).thenReturn(response(1, item(IDS.get(0))));
		when(client.fetchFacilities(2, 1)).thenReturn(response(2));
		when(client.fetchFacilities(3, 1)).thenReturn(response(3, item(IDS.get(2))));

		ParkingFacilityFullSyncStatus accepted = service.start(3, 1, false, null);
		jobId = accepted.jobId();
		ParkingFacilityFullSyncStatus completed = awaitTerminalStatus();

		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, completed.status());
		assertEquals(3, completed.lastSuccessPage());
		assertEquals(1, completed.skippedPageCount());
		assertTrue(completed.hasDataGaps());
		assertEquals(1, completed.skippedPages().size());
		assertEquals(2, completed.skippedPages().get(0).pageNo());
		assertEquals(ParkingSyncSkipReason.EMPTY_SUCCESS_PAGE,
				completed.skippedPages().get(0).reason());
		assertEquals(2, parkingLotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, IDS).size());
	}

	@Test
	void persistsEmptyObservationForRestartRecovery() {
		ParkingSyncJob job = jobService.create(1513, 1000, 1);
		jobId = job.getId();
		PublicDataFailure transientUpstream = new PublicDataFailure(
				PublicDataFailureCategory.TRANSIENT_UPSTREAM, "IncompletePublicDataPageException");
		jobService.recordEmptySuccessFailure(jobId, 1513, transientUpstream);
		jobService.recordPageFailure(jobId, 1513, transientUpstream);
		jobService.recordEmptySuccessFailure(jobId, 1513, transientUpstream);
		entityManager.clear();

		ParkingSyncJob reloaded = jobRepository.findById(jobId).orElseThrow();
		assertEquals(1513, reloaded.getCurrentEmptyPageNo());
		assertEquals(2, reloaded.getCurrentEmptySuccessCount());
	}

	@Test
	void stopsAfterTenTrailingEmptyPagesWithoutCallingRemainingProviderPages() throws Exception {
		when(client.fetchFacilities(org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.eq(1))).thenAnswer(invocation ->
				responseWithTotal(invocation.getArgument(0), "20"));
		when(client.fetchFacilities(1, 1)).thenReturn(
				responseWithTotal(1, "20", item(IDS.get(0))));

		ParkingFacilityFullSyncStatus accepted = service.start(20, 1, false, null);
		jobId = accepted.jobId();
		ParkingFacilityFullSyncStatus completed = awaitTerminalStatus();

		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, completed.status());
		assertEquals(ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES,
				completed.completionReason());
		assertEquals(11, completed.lastProcessedPage());
		assertEquals(1, completed.lastDataPage());
		assertEquals(10, completed.consecutiveEmptyPageCount());
		assertEquals(2, completed.firstTrailingEmptyPage());
		assertEquals(19L, completed.reportedRemainingCount());
		verify(client, never()).fetchFacilities(12, 1);
	}

	private ParkingFacilityFullSyncStatus awaitTerminalStatus() throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			ParkingFacilityFullSyncStatus status = service.status();
			if (status.status() == ParkingSyncJobStatus.COMPLETED
					|| status.status() == ParkingSyncJobStatus.COMPLETED_WITH_GAPS
					|| status.status() == ParkingSyncJobStatus.FAILED
					|| status.status() == ParkingSyncJobStatus.PAUSED) return status;
			Thread.sleep(20);
		}
		throw new AssertionError("Full sync worker did not finish in time");
	}

	private PublicParkingFacilityResponse response(int pageNo, PublicParkingFacilityItem... items) {
		return responseWithTotal(pageNo, "3", items);
	}

	private PublicParkingFacilityResponse responseWithTotal(int pageNo, String totalCount,
			PublicParkingFacilityItem... items) {
		return new PublicParkingFacilityResponse(List.of(items), Integer.toString(pageNo), "0",
				totalCount, "1", "SUCCESS");
	}

	private PublicParkingFacilityItem item(String id) {
		return new PublicParkingFacilityItem(id, "full integration", "address", "sido", "sigungu",
				"37.1234567", "127.1234567", "10");
	}
}
