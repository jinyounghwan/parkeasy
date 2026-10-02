package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.parkeasy.backend.parking.domain.ParkingOperation;
import com.parkeasy.backend.parking.domain.ParkingOperationDetails;

/** Synthetic contract fixtures, not captured production responses. No network calls. */
class ParkingOperationFixtureTest {
	private final ParkingOperationParser parser = new ParkingOperationParser();

	private Map<String, ParkingOperationDetails> fixtures() throws Exception {
		try (var input = getClass().getResourceAsStream("/fixtures/operation-cases.json")) {
			assertNotNull(input);
			var response = JsonMapper.builder().build().readValue(input, PublicParkingOperationResponse.class);
			assertEquals(9, response.safeItems().size());
			return response.safeItems().stream().collect(Collectors.toMap(
					PublicParkingOperationItem::externalId, parser::parse));
		}
	}

	@Test
	void everyFeeAndEveryDayMapsToItsOwnEntityField() throws Exception {
		var d = fixtures().get("normal");
		assertEquals(List.of(5, 30, 1200, 10, 300, 10000, 100000),
				List.of(d.baseFreeTime(), d.baseTime(), d.baseFee(), d.additionalTime(),
						d.additionalFee(), d.dailyMaxFee(), d.monthlyFee()));
		var opens = List.of(d.mondayOpenTime(), d.tuesdayOpenTime(), d.wednesdayOpenTime(), d.thursdayOpenTime(),
				d.fridayOpenTime(), d.saturdayOpenTime(), d.sundayOpenTime(), d.holidayOpenTime());
		var closes = List.of(d.mondayCloseTime(), d.tuesdayCloseTime(), d.wednesdayCloseTime(), d.thursdayCloseTime(),
				d.fridayCloseTime(), d.saturdayCloseTime(), d.sundayCloseTime(), d.holidayCloseTime());
		for (int i = 0; i < 8; i++) {
			assertEquals(LocalTime.of(i + 1, 0), opens.get(i));
			assertEquals(LocalTime.of(i + 11, 0), closes.get(i));
		}
		var entity = new ParkingOperation(null, d);
		for (var component : ParkingOperationDetails.class.getRecordComponents()) {
			String name = component.getName();
			var getter = ParkingOperation.class.getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
			assertEquals(component.getAccessor().invoke(d), getter.invoke(entity), name);
		}
	}

	@Test
	void zeroAmountsAndDurationsRemainKnownIncludingSixDigitNumericZero() throws Exception {
		var d = fixtures().get("zero");
		assertEquals(List.of(0, 0, 0, 0, 0, 0, 0), List.of(d.baseFreeTime(), d.baseTime(), d.baseFee(),
				d.additionalTime(), d.additionalFee(), d.dailyMaxFee(), d.monthlyFee()));
		assertTrue(parser.hasAnyValue(d));
		assertEquals(0, fixtures().get("additional-zero").additionalFee());
	}

	@Test
	void midnightPairIsUnknownButIndividualMidnightRemainsValid() throws Exception {
		var d = fixtures().get("midnight");
		assertNull(d.mondayOpenTime()); assertNull(d.mondayCloseTime());
		assertEquals(LocalTime.MIDNIGHT, d.tuesdayOpenTime());
		assertEquals(LocalTime.of(18, 0), d.tuesdayCloseTime());
		assertEquals(LocalTime.of(9, 0), d.wednesdayOpenTime());
		assertEquals(LocalTime.MIDNIGHT, d.wednesdayCloseTime());
	}

	@Test
	void blankMissingAndMalformedValuesBecomeNullWithoutInventedDefaults() throws Exception {
		for (String name : List.of("blank", "missing", "malformed")) {
			var d = fixtures().get(name);
			for (var component : ParkingOperationDetails.class.getRecordComponents()) {
				assertNull(component.getAccessor().invoke(d), name + ":" + component.getName());
			}
			assertFalse(parser.hasAnyValue(d));
		}
	}

	@Test
	void monthlyOnlyAndTimeOnlyRecordsAreRetained() throws Exception {
		var monthly = fixtures().get("monthly-only");
		assertEquals(250000, monthly.monthlyFee()); assertNull(monthly.baseFee()); assertNull(monthly.baseTime());
		assertTrue(parser.hasAnyValue(monthly));
		var time = fixtures().get("time-only");
		assertNull(time.baseFee()); assertEquals(LocalTime.of(9, 0), time.saturdayOpenTime());
		assertEquals(LocalTime.of(22, 0), time.saturdayCloseTime()); assertTrue(parser.hasAnyValue(time));
	}
}
