package com.parkeasy.backend.external.parking;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicParkingFacilityItem(
		@JsonProperty("prk_center_id") String externalId,
		@JsonProperty("prk_plce_nm") String name,
		@JsonProperty("prk_plce_adres") String address,
		@JsonProperty("prk_plce_adres_sido") String sido,
		@JsonProperty("prk_plce_adres_sigungu") String sigungu,
		@JsonProperty("prk_plce_entrc_la") String latitude,
		@JsonProperty("prk_plce_entrc_lo") String longitude,
		@JsonProperty("prk_cmprt_co") String totalSpaces) {
}
