package com.parkeasy.backend.parking.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingType;

public record ParkingLotDetailResponse(
		Long id,
		String name,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		ParkingType parkingType,
		Integer totalSpaces,
		Integer baseTime,
		Integer baseFee,
		Boolean isFree,
		Boolean is24Hours,
		Integer additionalTime,
		Integer additionalFee,
		Integer dailyMaxFee,
		LocalTime weekdayOpenTime,
		LocalTime weekdayCloseTime,
		DataSource dataSource,
		ParkingOperationResponse operation,
		ParkingRealtimeResponse realtime) {

	public static ParkingLotDetailResponse from(ParkingLot parkingLot, ParkingOperationResponse operation,
			ParkingRealtimeResponse realtime) {
		OperationSummary summary = OperationSummary.from(operation);
		return new ParkingLotDetailResponse(
				parkingLot.getId(),
				parkingLot.getName(),
				parkingLot.getAddress(),
				parkingLot.getLatitude(),
				parkingLot.getLongitude(),
				parkingLot.getParkingType(),
				parkingLot.getTotalSpaces(),
				summary.baseTime(),
				summary.baseFee(),
				summary.free(),
				summary.open24Hours(),
				operation == null ? null : operation.additionalTime(),
				operation == null ? null : operation.additionalFee(),
				operation == null ? null : operation.dailyMaxFee(),
				commonWeekdayTime(operation, true),
				commonWeekdayTime(operation, false),
				parkingLot.getDataSource(),
				operation,
				realtime);
	}

	private static LocalTime commonWeekdayTime(ParkingOperationResponse operation, boolean opening) {
		if (operation == null) return null;
		LocalTime[] times = opening
				? new LocalTime[] {operation.mondayOpenTime(), operation.tuesdayOpenTime(), operation.wednesdayOpenTime(), operation.thursdayOpenTime(), operation.fridayOpenTime()}
				: new LocalTime[] {operation.mondayCloseTime(), operation.tuesdayCloseTime(), operation.wednesdayCloseTime(), operation.thursdayCloseTime(), operation.fridayCloseTime()};
		return times[0] != null && java.util.Arrays.stream(times).allMatch(times[0]::equals) ? times[0] : null;
	}
}
