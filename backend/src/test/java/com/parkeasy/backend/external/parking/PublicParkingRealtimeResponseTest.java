package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class PublicParkingRealtimeResponseTest {
	@Test
	void mapsActualRealtimeFields() throws Exception {
		String json = """
				{"pageNo":"1","PrkRealtimeInfo":[{"pkfc_Available_ParkingLots_total":"54",
				"pkfc_ParkingLots_total":"131","prk_center_id":"center-1"}],"resultCode":"0",
				"totalCount":"1","numOfRows":"10","resultMsg":"SUCCESS"}
				""";
		PublicParkingRealtimeResponse response = JsonMapper.builder().build()
				.readValue(json, PublicParkingRealtimeResponse.class);
		assertEquals("center-1", response.items().get(0).externalId());
		assertEquals("131", response.items().get(0).totalSpaces());
		assertEquals("54", response.items().get(0).availableSpaces());
	}
}
