package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

class PublicDataParkingClientTest {

	@Test
	void preservesAlreadyEncodedServiceKeyWithoutDoubleEncoding() {
		String encodedKey = "sample%2Fencoded%2Bkey%3D%3D";
		PublicDataParkingClient client = new PublicDataParkingClient(
				"http://apis.data.go.kr/B553881/Parking/PrkSttusInfo", encodedKey);

		URI uri = client.buildUri(1, 10);
		String rawQuery = uri.getRawQuery();

		assertEquals("http", uri.getScheme());
		assertEquals("apis.data.go.kr", uri.getHost());
		assertEquals("/B553881/Parking/PrkSttusInfo", uri.getPath());
		assertTrue(rawQuery.contains("serviceKey=" + encodedKey));
		assertTrue(rawQuery.contains("numOfRows=10"));
		assertTrue(rawQuery.contains("pageNo=1"));
		assertTrue(rawQuery.contains("format=2"));
		assertFalse(rawQuery.contains("%252F"));
	}
}
