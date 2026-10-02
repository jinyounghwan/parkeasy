package com.parkeasy.backend.external.parking;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ParkingFacilityMapper {
	ParkingFacilityMappingResult map(PublicParkingFacilityItem item) {
		if (!StringUtils.hasText(item.externalId())) return ParkingFacilityMappingResult.invalid("MISSING_EXTERNAL_ID");
		if (!StringUtils.hasText(item.name())) return ParkingFacilityMappingResult.invalid("MISSING_NAME");
		BigDecimal latitude = parseDecimal(item.latitude());
		BigDecimal longitude = parseDecimal(item.longitude());
		if (latitude == null || longitude == null
				|| latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0
				|| longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
			return ParkingFacilityMappingResult.invalid("INVALID_COORDINATES");
		}
		return ParkingFacilityMappingResult.valid(new ParkingFacilityData(item.externalId().trim(), item.name().trim(),
				trimToNull(item.address()), latitude, longitude, parseNonNegativeInteger(item.totalSpaces())));
	}

	private BigDecimal parseDecimal(String value) {
		try { return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : null; }
		catch (NumberFormatException exception) { return null; }
	}

	private Integer parseNonNegativeInteger(String value) {
		try { int parsed = StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : -1; return parsed >= 0 ? parsed : null; }
		catch (NumberFormatException exception) { return null; }
	}

	private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
