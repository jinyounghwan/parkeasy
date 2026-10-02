package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingOperationTime(
		@JsonProperty("opertn_start_time") String startTime,
		@JsonProperty("opertn_end_time") String endTime) {
}
