package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingRealtimeItem(
		@JsonProperty("prk_center_id") String externalId,
		@JsonProperty("pkfc_ParkingLots_total") String totalSpaces,
		@JsonProperty("pkfc_Available_ParkingLots_total") String availableSpaces) {
}
