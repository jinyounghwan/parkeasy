package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class PublicParkingOperationSyncServiceTest {
	@Test
	void keepsLastValidDuplicateAndReportsCounts() {
		PublicParkingOperationClient client = mock(PublicParkingOperationClient.class);
		ParkingOperationUpsertService upsert = mock(ParkingOperationUpsertService.class);
		when(client.fetchOperations(1, 10)).thenReturn(new PublicParkingOperationResponse("1", List.of(
				item("same", "10"), item("same", "20"), item(null, "30")), "0", "3", "10", "SUCCESS"));
		when(upsert.upsert(anyCollection())).thenReturn(new ParkingOperationUpsertResult(1, 1, 0, 0));
		PublicParkingOperationSyncService service = new PublicParkingOperationSyncService(client,
				new ParkingOperationParser(), upsert);

		ParkingOperationSyncResult result = service.sync(1);

		assertEquals(3, result.fetchedCount()); assertEquals(1, result.matchedParkingLotCount());
		assertEquals(1, result.insertedCount()); assertEquals(1, result.skippedCount());
		assertEquals(1, result.duplicateCount());
		verify(upsert).upsert(org.mockito.ArgumentMatchers.argThat(values ->
				values.size() == 1 && values.iterator().next().details().baseTime() == 20));
	}

	private PublicParkingOperationItem item(String id, String baseTime) {
		return new PublicParkingOperationItem(id, null, null, null, null, null, null, null, null, null,
				new PublicParkingBasicInfo(baseTime, null, null, null), null);
	}
}
