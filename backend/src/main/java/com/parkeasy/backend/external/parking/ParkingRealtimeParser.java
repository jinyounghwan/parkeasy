package com.parkeasy.backend.external.parking;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ParkingRealtimeParser {
	public ParsedRealtime parse(PublicParkingRealtimeItem item) {
		Integer total = parseInteger(item.totalSpaces());
		Integer available = parseInteger(item.availableSpaces());
		boolean relationshipInvalid = (total != null && total < 0)
				|| (available != null && available < 0)
				|| (total != null && available != null && available > total);
		boolean valid = (total != null || available != null)
				&& !relationshipInvalid;
		return new ParsedRealtime(total, available, valid, total == null,
				available == null, relationshipInvalid);
	}

	Integer parseInteger(String value) {
		try { return StringUtils.hasText(value) ? Integer.valueOf(value.trim()) : null; }
		catch (NumberFormatException exception) { return null; }
	}

	public record ParsedRealtime(Integer totalSpaces, Integer availableSpaces, boolean valid,
			boolean missingTotalSpaces, boolean missingAvailableSpaces,
			boolean relationshipInvalid) { }
}
