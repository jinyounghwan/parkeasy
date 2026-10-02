package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingOperationItem(
		@JsonProperty("prk_center_id") String externalId,
		@JsonProperty("opertn_bs_free_time") String baseFreeTime,
		@JsonProperty("Monday") PublicParkingOperationTime monday,
		@JsonProperty("Tuesday") PublicParkingOperationTime tuesday,
		@JsonProperty("Wednesday") PublicParkingOperationTime wednesday,
		@JsonProperty("Thursday") PublicParkingOperationTime thursday,
		@JsonProperty("Friday") PublicParkingOperationTime friday,
		@JsonProperty("Saturday") PublicParkingOperationTime saturday,
		@JsonProperty("Sunday") PublicParkingOperationTime sunday,
		@JsonProperty("Holiday") PublicParkingOperationTime holiday,
		@JsonProperty("basic_info") PublicParkingBasicInfo basicInfo,
		@JsonProperty("fxamt_info") PublicParkingFixedAmountInfo fixedAmountInfo) {
}
