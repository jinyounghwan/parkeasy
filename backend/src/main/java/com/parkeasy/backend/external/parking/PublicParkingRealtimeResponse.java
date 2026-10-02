package com.parkeasy.backend.external.parking;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingRealtimeResponse(
		String pageNo,
		@JsonProperty("PrkRealtimeInfo") List<PublicParkingRealtimeItem> items,
		String resultCode, String totalCount, String numOfRows, String resultMsg) {
	public List<PublicParkingRealtimeItem> safeItems() { return items == null ? List.of() : items; }
}
