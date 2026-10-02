package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PublicParkingRealtimeApiLiveTest {
	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void fetchesOnlyFirstPageWithTenRows() {
		PublicParkingRealtimeClient client = new PublicDataParkingRealtimeClient(
				"http://apis.data.go.kr/B553881/Parking/PrkRealtimeInfo",
				System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"));
		PublicParkingRealtimeResponse response = client.fetchRealtime(1, 10);
		assertEquals("0", response.resultCode());
		assertTrue(response.safeItems().size() <= 10);
	}
}
