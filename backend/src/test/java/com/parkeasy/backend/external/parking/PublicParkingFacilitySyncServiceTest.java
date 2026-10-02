package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublicParkingFacilitySyncServiceTest {

	@Test
	void skipsInvalidCoordinatesFiltersRegionAndKeepsLastValidDuplicate() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		ParkingFacilityUpsertService upsertService = mock(ParkingFacilityUpsertService.class);
		when(client.fetchFacilities(1, 10)).thenReturn(response(List.of(
				item("A", "first", "경기도", "수원시 팔달구", "37.1", "127.1"),
				item("A", "last", "경기도", "수원시 팔달구", "37.2", "127.2"),
				item("B", "invalid", "경기도", "수원시", "not-number", "127.0"),
				item("C", "other", "경기도", "오산시", "37.0", "127.0"))));
		when(upsertService.upsert(anyCollection())).thenReturn(new ParkingFacilityUpsertResult(1, 0));
		PublicParkingFacilitySyncService service = new PublicParkingFacilitySyncService(
				client, new ParkingRegionFilter(), upsertService, new ParkingFacilityMapper());

		ParkingFacilitySyncResult result = service.sync("경기도", "수원", 1);

		assertEquals(4, result.fetchedCount());
		assertEquals(3, result.matchedCount());
		assertEquals(1, result.skippedCount());
		assertEquals(1, result.duplicateCount());
		assertEquals(1, result.insertedCount());
		ArgumentCaptor<java.util.Collection<ParkingFacilityData>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(upsertService).upsert(captor.capture());
		assertEquals("last", captor.getValue().iterator().next().name());
	}

	private PublicParkingFacilityResponse response(List<PublicParkingFacilityItem> items) {
		return new PublicParkingFacilityResponse(items, "1", "0", "4", "10", "SUCCESS");
	}

	private PublicParkingFacilityItem item(String id, String name, String sido, String sigungu,
			String latitude, String longitude) {
		return new PublicParkingFacilityItem(id, name, "address", sido, sigungu, latitude, longitude, "10");
	}
}
