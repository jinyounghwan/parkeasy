package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.ParkingOperationDetails;

class ParkingOperationParserTest {
	private final ParkingOperationParser parser = new ParkingOperationParser();

	@Test
	void parsesTimesAndSafeIntegers() {
		ParkingOperationDetails details = parser.parse(item("0", "30", "invalid",
				new PublicParkingOperationTime("090000", "180000")));
		assertEquals(LocalTime.of(9, 0), details.mondayOpenTime());
		assertEquals(LocalTime.of(18, 0), details.mondayCloseTime());
		assertEquals(0, details.baseFreeTime());
		assertEquals(30, details.baseTime());
		assertNull(details.baseFee());
	}

	@Test
	void treatsMidnightToMidnightAsUnknownOrClosed() {
		ParkingOperationDetails details = parser.parse(item(null, null, null,
				new PublicParkingOperationTime("000000", "000000")));
		assertNull(details.mondayOpenTime());
		assertNull(details.mondayCloseTime());
	}

	private PublicParkingOperationItem item(String free, String baseTime, String baseFee,
			PublicParkingOperationTime monday) {
		return new PublicParkingOperationItem("id", free, monday, null, null, null, null, null, null, null,
				new PublicParkingBasicInfo(baseTime, baseFee, null, null),
				new PublicParkingFixedAmountInfo(null, null));
	}
}
