package com.parkeasy.backend.parking.dto;

import java.math.BigDecimal;

import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingType;

public record ParkingLotSummaryResponse(
		Long id,
		String name,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		ParkingType parkingType,
		Integer totalSpaces,
		Integer baseTime,
		Integer baseFee,
		boolean isFree,
		boolean is24Hours) {

	public static ParkingLotSummaryResponse from(ParkingLot parkingLot) {
		return new ParkingLotSummaryResponse(
				parkingLot.getId(),
				parkingLot.getName(),
				parkingLot.getAddress(),
				parkingLot.getLatitude(),
				parkingLot.getLongitude(),
				parkingLot.getParkingType(),
				parkingLot.getTotalSpaces(),
				parkingLot.getBaseTime(),
				parkingLot.getBaseFee(),
				parkingLot.isFree(),
				parkingLot.isOpen24Hours());
	}
}
