package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
class PublicParkingApiLiveTest {

	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void fetchesOnlyOnePageWithOneHundredRows() {
		PublicParkingClient client = new PublicDataParkingClient(
				"http://apis.data.go.kr/B553881/Parking/PrkSttusInfo",
				System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"));

		PublicParkingFacilityResponse response = client.fetchFacilities(1, 100);

		assertEquals("0", response.resultCode());
		assertTrue(response.safeItems().size() <= 100);
	}
}
