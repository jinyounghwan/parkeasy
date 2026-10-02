package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublicParkingRealtimeSyncServiceTest {
	@Test
	void keepsLastValidDuplicateAndReportsCounts() {
		PublicParkingRealtimeClient client = mock(PublicParkingRealtimeClient.class);
		ParkingRealtimeUpsertService upsert = mock(ParkingRealtimeUpsertService.class);
		when(client.fetchRealtime(1, 10)).thenReturn(new PublicParkingRealtimeResponse("1", List.of(
				new PublicParkingRealtimeItem("same", "10", "3"),
				new PublicParkingRealtimeItem("same", "10", "4"),
				new PublicParkingRealtimeItem("same", "10", "11"),
				new PublicParkingRealtimeItem(null, "10", "2")), "0", "4", "10", "SUCCESS"));
		when(upsert.upsert(anyCollection())).thenReturn(new ParkingRealtimeUpsertResult(1, 1, 0, 0));
		PublicParkingRealtimeSyncService service = new PublicParkingRealtimeSyncService(
				client, new ParkingRealtimeParser(), upsert);

		ParkingRealtimeSyncResult result = service.sync(1);

		assertEquals(4, result.fetchedCount()); assertEquals(1, result.matchedParkingLotCount());
		assertEquals(1, result.insertedCount()); assertEquals(2, result.skippedCount());
		assertEquals(2, result.duplicateCount());
		verify(upsert).upsert(org.mockito.ArgumentMatchers.argThat(values ->
				values.size() == 1 && values.iterator().next().availableSpaces() == 4));
	}
}
