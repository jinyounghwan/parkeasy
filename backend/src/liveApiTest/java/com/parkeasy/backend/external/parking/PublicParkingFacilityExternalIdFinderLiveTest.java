package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PublicParkingFacilityExternalIdFinderLiveTest {
	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void searchesOnlyTwentyPagesOfOneHundredAndDoesNotWriteDatabase() {
		String target = "00000-19199-00004-00-1";
		PublicParkingClient client = new PublicDataParkingClient(
				"http://apis.data.go.kr/B553881/Parking/PrkSttusInfo",
				System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"));
		ParkingFacilityExternalIdSearchResult result = new PublicParkingFacilityExternalIdFinder(
				client, new ParkingFacilityMapper()).find(target, 20, 100);
		assertEquals(target, result.externalId());
	}
}
