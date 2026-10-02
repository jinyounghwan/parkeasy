package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublicParkingRealtimeFullPageServiceTest {
	@Test
	void validatesDeduplicatesAndUsesOneReceivedAtForThePage() {
		ParkingRealtimeUpsertService upsert = mock(ParkingRealtimeUpsertService.class);
		ParkingSyncJobRepository jobs = mock(ParkingSyncJobRepository.class);
		ParkingSyncJob job = ParkingSyncJob.realtimeFull(1, 1000, 1);
		when(jobs.findById(1L)).thenReturn(Optional.of(job));
		when(upsert.upsertBatch(anyCollection())).thenReturn(new ParkingRealtimeFullUpsertResult(2, 2, 0, 1, 1, 0));
		PublicParkingRealtimeFullPageService service = new PublicParkingRealtimeFullPageService(
				new ParkingRealtimeParser(), upsert, jobs, mock(ParkingSyncSkippedPageRepository.class));

		ParkingRealtimeFullPageResult result = service.process(1L, 1,
				response(item("same", "10", "3"), item("same", "bad", "bad"),
						item("same", "20", "0"), item("orphan", null, "2"),
						item(null, "10", "1"), item("negative", "10", "-1")), 6, 1);

		assertEquals(6, result.processed()); assertEquals(2, result.metrics().duplicateCount());
		assertEquals(1, result.metrics().missingExternalIdCount());
		assertEquals(2, result.metrics().invalidRealtimeCount());
		assertEquals(1, result.metrics().zeroAvailableCount());
		assertEquals(1, result.metrics().fullParkingCount());
		assertEquals(1, result.metrics().relationshipInvalidCount());
		ArgumentCaptor<java.util.Collection<ParkingRealtimeCandidate>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(upsert).upsertBatch(captor.capture());
		var values = captor.getValue();
		assertEquals(2, values.size());
		assertEquals(1, values.stream().map(ParkingRealtimeCandidate::receivedAt).distinct().count());
		assertEquals(0, values.stream().filter(value -> value.externalId().equals("same"))
				.findFirst().orElseThrow().availableSpaces());
	}

	private PublicParkingRealtimeResponse response(PublicParkingRealtimeItem... items) {
		return new PublicParkingRealtimeResponse("1", List.of(items), "0", "6", "1000", "SUCCESS");
	}
	private PublicParkingRealtimeItem item(String id, String total, String available) {
		return new PublicParkingRealtimeItem(id, total, available);
	}
}
