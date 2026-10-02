package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OperationQualityMetricsTest {
	@Test
	void distinguishesMissingMalformedAndIntentionalMidnightPairWithoutCountingZerosAsMissing() throws Exception {
		PublicParkingOperationResponse response;
		try (var input = getClass().getResourceAsStream("/fixtures/operation-cases.json")) {
			response = JsonMapper.builder().build().readValue(input, PublicParkingOperationResponse.class);
		}
		var parser = new ParkingOperationParser();
		for (var item : response.safeItems()) {
			var metric = OperationQualityMetrics.inspect(List.of(item), parser);
			switch (item.externalId()) {
				case "normal", "zero" -> {
					assertTrue(metric.numericMissing().values().stream().allMatch(n -> n == 0));
					assertTrue(metric.numericMalformed().values().stream().allMatch(n -> n == 0));
				}
				case "blank", "missing" -> {
					assertTrue(metric.numericMissing().values().stream().allMatch(n -> n == 1));
					assertTrue(metric.numericMalformed().values().stream().allMatch(n -> n == 0));
					assertEquals(16, metric.timeMissing()); assertEquals(0, metric.timeMalformed());
				}
				case "malformed" -> {
					assertTrue(metric.numericMissing().values().stream().allMatch(n -> n == 0));
					assertTrue(metric.numericMalformed().values().stream().allMatch(n -> n == 1));
					assertEquals(6, metric.timeMalformed()); assertEquals(10, metric.timeMissing());
				}
				case "midnight" -> {
					assertEquals(1, metric.midnightPairs()); assertEquals(10, metric.timeMissing());
					assertEquals(0, metric.timeMalformed());
				}
			}
		}
		var metrics = OperationQualityMetrics.inspect(response.safeItems(), parser);
		assertEquals(9, metrics.items());
		assertEquals(5, metrics.numericMissing().get("baseFee"));
		assertEquals(1, metrics.numericMalformed().get("baseFee"));
		assertEquals(1, metrics.midnightPairs());
		assertFalse(metrics.toString().contains("1200"));
		assertFalse(metrics.toString().contains("monthly-only"));
	}

	@Test
	void duplicatesAreCountedAsOccurrencesAndEmptyPageIsZero() {
		var parser = new ParkingOperationParser();
		assertEquals(0, OperationQualityMetrics.inspect(List.of(), parser).items());
		var item = new PublicParkingOperationItem("sensitive-test-marker", null, null, null, null, null,
				null, null, null, null, null, null);
		var metric = OperationQualityMetrics.inspect(List.of(item, item), parser);
		assertEquals(2, metric.numericMissing().get("baseFee")); assertEquals(32, metric.timeMissing());
		assertFalse(metric.toString().contains("sensitive-test-marker"));
	}
}
