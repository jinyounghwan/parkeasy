package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PublicParkingFacilityResponseTest {

	@Test
	void mapsActualJsonFieldNames() throws Exception {
		String json = """
				{"PrkSttusInfo":[{"prk_plce_adres_sido":"서울특별시","prk_cmprt_co":"38",
				"prk_center_id":"01016-11191-00001-00-1","prk_plce_entrc_lo":"127.0053218",
				"prk_plce_entrc_la":"37.6428716","prk_plce_adres_sigungu":"강북구",
				"prk_plce_nm":"아카데미주차장","prk_plce_adres":"서울특별시 강북구 419로25길"}],
				"pageNo":"1","resultCode":"0","totalCount":"1767934","numOfRows":"10","resultMsg":"SUCCESS"}
				""";
		ObjectMapper objectMapper = JsonMapper.builder().build();

		PublicParkingFacilityResponse response = objectMapper.readValue(
				json, PublicParkingFacilityResponse.class);

		PublicParkingFacilityItem item = response.items().get(0);
		assertEquals("01016-11191-00001-00-1", item.externalId());
		assertEquals("아카데미주차장", item.name());
		assertEquals("서울특별시 강북구 419로25길", item.address());
		assertEquals("서울특별시", item.sido());
		assertEquals("강북구", item.sigungu());
		assertEquals("37.6428716", item.latitude());
		assertEquals("127.0053218", item.longitude());
		assertEquals("38", item.totalSpaces());
	}
}
