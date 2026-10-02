package com.parkeasy.backend.parking.dto;

import java.time.LocalTime;

import com.parkeasy.backend.parking.domain.ParkingOperation;

public record ParkingOperationResponse(
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
	public static ParkingOperationResponse from(ParkingOperation operation) {
		if (operation == null) return null;
		return new ParkingOperationResponse(operation.getBaseFreeTime(), operation.getBaseTime(), operation.getBaseFee(),
				operation.getAdditionalTime(), operation.getAdditionalFee(), operation.getDailyMaxFee(), operation.getMonthlyFee(),
				operation.getMondayOpenTime(), operation.getMondayCloseTime(), operation.getTuesdayOpenTime(), operation.getTuesdayCloseTime(),
				operation.getWednesdayOpenTime(), operation.getWednesdayCloseTime(), operation.getThursdayOpenTime(), operation.getThursdayCloseTime(),
				operation.getFridayOpenTime(), operation.getFridayCloseTime(), operation.getSaturdayOpenTime(), operation.getSaturdayCloseTime(),
				operation.getSundayOpenTime(), operation.getSundayCloseTime(), operation.getHolidayOpenTime(), operation.getHolidayCloseTime());
	}
}
