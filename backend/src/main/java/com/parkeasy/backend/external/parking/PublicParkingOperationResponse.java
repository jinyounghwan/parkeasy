package com.parkeasy.backend.external.parking;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingOperationResponse(
		String pageNo,
		@JsonProperty("PrkOprInfo") List<PublicParkingOperationItem> items,
		String resultCode,
		String totalCount,
		String numOfRows,
		String resultMsg) {
	public List<PublicParkingOperationItem> safeItems() { return items == null ? List.of() : items; }
}
