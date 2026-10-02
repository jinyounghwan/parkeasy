package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PublicParkingOperationApiLiveTest {
	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void fetchesOnlyFirstPageWithTenRows() {
		PublicParkingOperationClient client = new PublicDataParkingOperationClient(
				"http://apis.data.go.kr/B553881/Parking/PrkOprInfo",
				System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"));
		PublicParkingOperationResponse response = client.fetchOperations(1, 10);
		assertEquals("0", response.resultCode());
		assertTrue(response.safeItems().size() <= 10);
	}
}
