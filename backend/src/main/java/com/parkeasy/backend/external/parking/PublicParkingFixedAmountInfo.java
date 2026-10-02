package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingFixedAmountInfo(
		@JsonProperty("parking_chrge_one_day_chrge") String dailyMaxFee,
		@JsonProperty("parking_chrge_mon_unit_chrge") String monthlyFee) {
}
