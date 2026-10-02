package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParkingRealtimeParserTest {
	private final ParkingRealtimeParser parser = new ParkingRealtimeParser();

	@Test
	void parsesZeroAvailableAsValid() {
		var parsed = parser.parse(new PublicParkingRealtimeItem("id", "131", "0"));
		assertTrue(parsed.valid()); assertEquals(131, parsed.totalSpaces()); assertEquals(0, parsed.availableSpaces());
	}

	@Test
	void invalidStringOnlyNullsThatField() {
		var parsed = parser.parse(new PublicParkingRealtimeItem("id", "invalid", "3"));
		assertTrue(parsed.valid()); assertNull(parsed.totalSpaces()); assertEquals(3, parsed.availableSpaces());
		assertTrue(parsed.missingTotalSpaces()); assertFalse(parsed.missingAvailableSpaces());
	}

	@Test
	void rejectsNegativeOrAvailableGreaterThanTotal() {
		assertTrue(parser.parse(new PublicParkingRealtimeItem("id", "10", "-1")).relationshipInvalid());
		assertTrue(parser.parse(new PublicParkingRealtimeItem("id", "10", "11")).relationshipInvalid());
	}

	@Test
	void rejectsBothMissingButAcceptsZeroAvailable() {
		assertFalse(parser.parse(new PublicParkingRealtimeItem("id", "bad", "")).valid());
		assertTrue(parser.parse(new PublicParkingRealtimeItem("id", "10", "0")).valid());
	}
}
