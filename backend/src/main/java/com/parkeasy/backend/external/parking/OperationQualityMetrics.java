package com.parkeasy.backend.external.parking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Counts raw DTO occurrences before deduplication; never contains raw values or identifiers. */
public record OperationQualityMetrics(int items, Map<String, Integer> numericMissing,
		Map<String, Integer> numericMalformed, int timeMissing, int timeMalformed, int midnightPairs) {

	public static OperationQualityMetrics inspect(List<PublicParkingOperationItem> items, ParkingOperationParser parser) {
		Map<String, Integer> missing = new LinkedHashMap<>(), malformed = new LinkedHashMap<>();
		String[] fields = {"baseFreeTime", "baseTime", "baseFee", "additionalTime", "additionalFee", "dailyMaxFee", "monthlyFee"};
		for (String field : fields) { missing.put(field, 0); malformed.put(field, 0); }
		int timeMissing = 0, timeMalformed = 0, midnightPairs = 0;
		for (var item : items) {
			var b = item.basicInfo(); var f = item.fixedAmountInfo();
			String[] values = {item.baseFreeTime(), b == null ? null : b.baseTime(), b == null ? null : b.baseFee(),
					b == null ? null : b.additionalTime(), b == null ? null : b.additionalFee(),
					f == null ? null : f.dailyMaxFee(), f == null ? null : f.monthlyFee()};
			for (int i = 0; i < fields.length; i++) {
				if (!StringUtils.hasText(values[i])) missing.merge(fields[i], 1, Integer::sum);
				else if (parser.parseInteger(values[i]) == null) malformed.merge(fields[i], 1, Integer::sum);
			}
			PublicParkingOperationTime[] days = {item.monday(), item.tuesday(), item.wednesday(), item.thursday(),
					item.friday(), item.saturday(), item.sunday(), item.holiday()};
			for (var day : days) {
				if (day == null) { timeMissing += 2; continue; }
				if ("000000".equals(trim(day.startTime())) && "000000".equals(trim(day.endTime()))) {
					midnightPairs++; continue;
				}
				var parsed = parser.parseRange(day);
				if (!StringUtils.hasText(day.startTime())) timeMissing++;
				else if (parsed.open() == null) timeMalformed++;
				if (!StringUtils.hasText(day.endTime())) timeMissing++;
				else if (parsed.close() == null) timeMalformed++;
			}
		}
		return new OperationQualityMetrics(items.size(), Map.copyOf(missing), Map.copyOf(malformed),
				timeMissing, timeMalformed, midnightPairs);
	}

	private static String trim(String value) { return value == null ? null : value.trim(); }
}
