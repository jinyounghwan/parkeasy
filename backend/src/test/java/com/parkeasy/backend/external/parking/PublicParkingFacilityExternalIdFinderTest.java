package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PublicParkingFacilityExternalIdFinderTest {
	private static final String TARGET = "target-id";

	@Test
	void findsOnFirstPage() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		when(client.fetchFacilities(1, 100)).thenReturn(response(1, item(TARGET, "found", "37", "127")));
		var result = finder(client).find(TARGET, 20, 100);
		assertTrue(result.found()); assertEquals(1, result.searchedPages()); assertEquals("found", result.data().name());
		verify(client, never()).fetchFacilities(2, 100);
	}

	@Test
	void stopsImmediatelyWhenFoundOnMiddlePage() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		when(client.fetchFacilities(1, 100)).thenReturn(response(1, item("other", "other", "37", "127")));
		when(client.fetchFacilities(2, 100)).thenReturn(response(2, item(TARGET, "found", "37", "127")));
		var result = finder(client).find(TARGET, 20, 100);
		assertEquals(2, result.searchedPages()); verify(client, never()).fetchFacilities(3, 100);
	}

	@Test
	void reportsNotFoundAfterConfiguredLimit() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		for (int page = 1; page <= 3; page++) when(client.fetchFacilities(page, 10))
				.thenReturn(response(page, item("other-" + page, "other", "37", "127")));
		var result = finder(client).find(TARGET, 3, 10);
		assertFalse(result.found()); assertEquals(3, result.searchedPages()); assertEquals("NOT_FOUND", result.failureReason());
	}

	@Test
	void keepsLastValidDuplicateWhenLaterDuplicateIsInvalid() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		when(client.fetchFacilities(1, 100)).thenReturn(response(1,
				item(TARGET, "first", "37.1", "127.1"), item(TARGET, "last-valid", "37.2", "127.2"),
				item(TARGET, "invalid", "bad", "127.3")));
		var result = finder(client).find(TARGET, 20, 100);
		assertEquals(2, result.duplicateCount()); assertEquals("last-valid", result.data().name());
	}

	@Test
	void reportsInvalidCoordinatesWithoutSavingCandidate() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		when(client.fetchFacilities(1, 100)).thenReturn(response(1, item(TARGET, "invalid", "91", "127")));
		var result = finder(client).find(TARGET, 20, 100);
		assertTrue(result.found()); assertNull(result.data()); assertEquals("INVALID_COORDINATES", result.failureReason());
	}

	@Test
	void propagatesTimeoutAndDoesNotRequestLaterPage() {
		PublicParkingClient client = mock(PublicParkingClient.class);
		when(client.fetchFacilities(1, 100)).thenThrow(new ResourceAccessException("timeout"));
		assertThrows(ResourceAccessException.class, () -> finder(client).find(TARGET, 20, 100));
		verify(client, never()).fetchFacilities(2, 100);
	}

	private PublicParkingFacilityExternalIdFinder finder(PublicParkingClient client) {
		return new PublicParkingFacilityExternalIdFinder(client, new ParkingFacilityMapper());
	}
	private PublicParkingFacilityResponse response(int page, PublicParkingFacilityItem... items) {
		return new PublicParkingFacilityResponse(List.of(items), String.valueOf(page), "0", "9999", "100", "SUCCESS");
	}
	private PublicParkingFacilityItem item(String id, String name, String latitude, String longitude) {
		return new PublicParkingFacilityItem(id, name, "address", "sido", "sigungu", latitude, longitude, "10");
	}
}
