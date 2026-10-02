package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingRealtime;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;

@ActiveProfiles("realtime-full-integration")
@SpringBootTest(properties = {
		"parkeasy.public-data.full-sync.realtime.num-of-rows=1000",
		"parkeasy.public-data.full-sync.realtime.page-delay-ms=0",
		"parkeasy.public-data.full-sync.realtime.auto-resume-delay-ms=1",
		"parkeasy.public-data.full-sync.realtime.max-auto-resume-attempts=5",
		"parkeasy.public-data.full-sync.realtime.empty-page-skip-threshold=3",
		"parkeasy.public-data.full-sync.realtime.trailing-empty-page-threshold=10" })
class PublicParkingRealtimeFullSyncIntegrationTest {
	@Autowired PublicParkingRealtimeFullSyncService service;
	@Autowired ParkingLotRepository lots;
	@Autowired ParkingRealtimeRepository realtime;
	@MockitoBean PublicParkingRealtimeClient client;

	@Test
	void updatesLatestRowsResumesTimeoutAndStopsAtTrailingEmptyPages() throws Exception {
		ParkingLot lot1 = lots.save(lot("P1-0"));
		lots.saveAll(List.of(lot("P2-0"), lot("P3-0")));
		LocalDateTime oldReceivedAt = LocalDateTime.now().minusHours(1);
		realtime.save(new ParkingRealtime(lot1, 1, 1, null, oldReceivedAt));
		when(client.fetchRealtime(1, 1000)).thenReturn(response(1, items("P1-", 1000)));
		when(client.fetchRealtime(2, 1000)).thenReturn(response(2, items("P2-", 1000)));
		when(client.fetchRealtime(3, 1000))
				.thenThrow(new ResourceAccessException("timeout", new HttpTimeoutException("timed out")))
				.thenReturn(response(3, items("P3-", 1000)));
		when(client.fetchRealtime(org.mockito.ArgumentMatchers.intThat(page -> page >= 4), anyInt()))
				.thenAnswer(invocation -> response(invocation.getArgument(0), List.of()));

		ParkingRealtimeFullSyncStatus accepted = service.start(20, 1, false, null);
		ParkingRealtimeFullSyncStatus completed = awaitTerminal(accepted.jobId());

		assertEquals(ParkingSyncJobStatus.COMPLETED_WITH_GAPS, completed.status());
		assertEquals(ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES, completed.completionReason());
		assertEquals(13, completed.lastProcessedPage()); assertEquals(3, completed.lastDataPage());
		assertEquals(3000, completed.processedCount()); assertEquals(2, completed.insertedCount());
		assertEquals(1, completed.updatedCount()); assertEquals(2997, completed.orphanCount());
		assertEquals(3, realtime.count());
		ParkingRealtime updated = realtime.findByParkingLotId(lot1.getId()).orElseThrow();
		assertTrue(updated.getReceivedAt().isAfter(oldReceivedAt));
		verify(client, never()).fetchRealtime(14, 1000);
	}

	private ParkingRealtimeFullSyncStatus awaitTerminal(long jobId) throws InterruptedException {
		for (int attempt = 0; attempt < 500; attempt++) {
			ParkingRealtimeFullSyncStatus status = service.status(jobId);
			if (status.status() == ParkingSyncJobStatus.COMPLETED_WITH_GAPS
					|| status.status() == ParkingSyncJobStatus.COMPLETED
					|| status.status() == ParkingSyncJobStatus.FAILED) return status;
			Thread.sleep(10);
		}
		throw new AssertionError("Realtime full sync did not finish");
	}
	private ParkingLot lot(String externalId) {
		return ParkingLot.builder().name(externalId).latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
				.dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId(externalId).build();
	}
	private List<PublicParkingRealtimeItem> items(String prefix, int count) {
		return IntStream.range(0, count).mapToObj(index ->
				new PublicParkingRealtimeItem(prefix + index, "100", index == 0 ? "0" : "50")).toList();
	}
	private PublicParkingRealtimeResponse response(int pageNo, List<PublicParkingRealtimeItem> items) {
		return new PublicParkingRealtimeResponse(Integer.toString(pageNo), items, "0", "20000", "1000", "SUCCESS");
	}
}
