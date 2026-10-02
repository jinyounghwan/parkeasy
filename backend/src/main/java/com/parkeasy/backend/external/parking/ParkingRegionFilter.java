package com.parkeasy.backend.external.parking;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class ParkingRegionFilter {

	public boolean matches(PublicParkingFacilityItem item, String sido, String sigungu) {
		String expectedSido = normalize(sido);
		String actualSido = normalize(item.sido());
		String expectedSigungu = normalize(sigungu);
		String actualSigungu = normalize(item.sigungu());
		return !expectedSido.isEmpty()
				&& expectedSido.equals(actualSido)
				&& !expectedSigungu.isEmpty()
				&& (actualSigungu.startsWith(expectedSigungu)
						|| expectedSigungu.startsWith(actualSigungu));
	}

	private String normalize(String value) {
		return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}
}
