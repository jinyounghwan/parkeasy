package com.parkeasy.backend.external.parking;

import java.math.BigDecimal;

record ParkingFacilityData(
		String externalId,
		String name,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		Integer totalSpaces) {
}
