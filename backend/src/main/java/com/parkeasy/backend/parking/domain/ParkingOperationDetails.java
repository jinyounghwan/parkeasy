package com.parkeasy.backend.parking.domain;

import java.time.LocalTime;

public record ParkingOperationDetails(
		Integer baseFreeTime, Integer baseTime, Integer baseFee,
		Integer additionalTime, Integer additionalFee, Integer dailyMaxFee, Integer monthlyFee,
		LocalTime mondayOpenTime, LocalTime mondayCloseTime,
		LocalTime tuesdayOpenTime, LocalTime tuesdayCloseTime,
		LocalTime wednesdayOpenTime, LocalTime wednesdayCloseTime,
		LocalTime thursdayOpenTime, LocalTime thursdayCloseTime,
		LocalTime fridayOpenTime, LocalTime fridayCloseTime,
		LocalTime saturdayOpenTime, LocalTime saturdayCloseTime,
		LocalTime sundayOpenTime, LocalTime sundayCloseTime,
		LocalTime holidayOpenTime, LocalTime holidayCloseTime) {
}
