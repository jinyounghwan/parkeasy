package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class PublicParkingOperationResponseTest {
	@Test
	void mapsNestedOperationResponse() throws Exception {
		String json = """
				{"pageNo":"1","PrkOprInfo":[{"prk_center_id":"center-1","opertn_bs_free_time":"0",
				"Monday":{"opertn_start_time":"090000","opertn_end_time":"180000"},
				"basic_info":{"parking_chrge_bs_time":"30","parking_chrge_bs_chrge":"1200",
				"parking_chrge_adit_unit_time":"10","parking_chrge_adit_unit_chrge":"0"},
				"fxamt_info":{"parking_chrge_one_day_chrge":"10000","parking_chrge_mon_unit_chrge":"100000"}}],
				"resultCode":"0","totalCount":"1","numOfRows":"10","resultMsg":"SUCCESS"}
				""";
		PublicParkingOperationResponse response = JsonMapper.builder().build()
				.readValue(json, PublicParkingOperationResponse.class);
		assertEquals("center-1", response.items().get(0).externalId());
		assertEquals("090000", response.items().get(0).monday().startTime());
		assertEquals("1200", response.items().get(0).basicInfo().baseFee());
		assertEquals("100000", response.items().get(0).fixedAmountInfo().monthlyFee());
	}
}
