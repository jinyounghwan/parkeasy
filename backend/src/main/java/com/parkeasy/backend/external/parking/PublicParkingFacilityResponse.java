package com.parkeasy.backend.external.parking;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingFacilityResponse(
		@JsonProperty("PrkSttusInfo") List<PublicParkingFacilityItem> items,
		String pageNo,
		String resultCode,
		String totalCount,
		String numOfRows,
		String resultMsg) {

	public List<PublicParkingFacilityItem> safeItems() {
		return items == null ? List.of() : items;
	}
}
