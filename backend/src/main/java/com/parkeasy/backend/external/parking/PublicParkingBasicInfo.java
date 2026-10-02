package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingBasicInfo(
		@JsonProperty("parking_chrge_bs_time") String baseTime,
		@JsonProperty("parking_chrge_bs_chrge") String baseFee,
		@JsonProperty("parking_chrge_adit_unit_time") String additionalTime,
		@JsonProperty("parking_chrge_adit_unit_chrge") String additionalFee) {
}
