package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParkingRegionFilterTest {
	private final ParkingRegionFilter filter = new ParkingRegionFilter();

	@Test
	void acceptsCityPrefixForDistrictLevelApiValue() {
		assertTrue(filter.matches(item("경기도", "수원시 팔달구"), "경기도", "수원"));
		assertTrue(filter.matches(item("경기도", "수원시 팔달구"), "경기도", "수원시"));
	}

	@Test
	void rejectsDifferentSidoOrNonPrefixSigungu() {
		assertFalse(filter.matches(item("서울특별시", "수원시 팔달구"), "경기도", "수원"));
		assertFalse(filter.matches(item("경기도", "성남시 수원구"), "경기도", "수원"));
	}

	private PublicParkingFacilityItem item(String sido, String sigungu) {
		return new PublicParkingFacilityItem("id", "name", "address", sido, sigungu, "37", "127", "1");
	}
}
