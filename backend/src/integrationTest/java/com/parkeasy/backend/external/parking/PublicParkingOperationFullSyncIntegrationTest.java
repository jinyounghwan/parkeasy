package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;

@ActiveProfiles("operation-full-integration")
@SpringBootTest(properties = {
		"parkeasy.public-data.full-sync.operation.num-of-rows=2",
		"parkeasy.public-data.full-sync.operation.page-delay-ms=0",
		"parkeasy.public-data.full-sync.operation.auto-resume-delay-ms=1",
		"parkeasy.public-data.full-sync.operation.max-auto-resume-attempts=5",
		"parkeasy.public-data.full-sync.operation.empty-page-skip-threshold=3",
		"parkeasy.public-data.full-sync.operation.trailing-empty-page-threshold=10" })
class PublicParkingOperationFullSyncIntegrationTest {
	@Autowired PublicParkingOperationFullSyncService service;
	@Autowired ParkingLotRepository lotRepository;
	@Autowired ParkingOperationRepository operationRepository;
	@MockitoBean PublicParkingOperationClient client;

	@Test
	void resumesTimeoutThenStopsAfterTrailingEmptyPagesWithoutPollutingLocalMysql() throws Exception {
		lotRepository.saveAll(List.of(lot("A"), lot("B"), lot("C")));
		when(client.fetchOperations(1, 2)).thenReturn(response(1, item("A", "10"), item("ORPHAN", "20")));
		when(client.fetchOperations(2, 2)).thenReturn(response(2, item("B", "30"), item("A", "40")));
		when(client.fetchOperations(3, 2))
				.thenThrow(new ResourceAccessException("timeout", new HttpTimeoutException("timed out")))
				.thenReturn(response(3, item("C", "50"), item(null, "60")));
		when(client.fetchOperations(org.mockito.ArgumentMatchers.intThat(page -> page >= 4), anyInt()))
				.thenAnswer(invocation -> response(invocation.getArgument(0)));

		ParkingOperationFullSyncStatus accepted = service.start(20, 1, false, null);
		ParkingOperationFullSyncStatus completed = awaitTerminal(accepted.jobId());

		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, completed.status());
		assertEquals(ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES, completed.completionReason());
		assertEquals(13, completed.lastProcessedPage());
		assertEquals(3, completed.lastDataPage());
		assertEquals(6, completed.processedCount());
		assertEquals(3, completed.insertedCount());
		assertEquals(1, completed.updatedCount());
		assertEquals(1, completed.orphanCount());
		assertEquals(1, completed.missingExternalIdCount());
		assertEquals(3, operationRepository.count());
		verify(client, never()).fetchOperations(14, 2);
	}

	private ParkingOperationFullSyncStatus awaitTerminal(long jobId) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			ParkingOperationFullSyncStatus status = service.status(jobId);
			if (status.status() == ParkingSyncJobStatus.COMPLETED_WITH_GAPS
					|| status.status() == ParkingSyncJobStatus.COMPLETED
					|| status.status() == ParkingSyncJobStatus.FAILED) return status;
			Thread.sleep(10);
		}
		throw new AssertionError("Operation full sync did not finish");
	}

	private ParkingLot lot(String externalId) {
		return ParkingLot.builder().name(externalId).latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
				.dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId(externalId).build();
	}
	private PublicParkingOperationResponse response(int pageNo, PublicParkingOperationItem... items) {
		return new PublicParkingOperationResponse(Integer.toString(pageNo), List.of(items),
				"0", "40", "2", "SUCCESS");
	}
	private PublicParkingOperationItem item(String externalId, String baseTime) {
		return new PublicParkingOperationItem(externalId, null, null, null, null, null, null, null, null, null,
				new PublicParkingBasicInfo(baseTime, null, null, null), null);
	}
}
