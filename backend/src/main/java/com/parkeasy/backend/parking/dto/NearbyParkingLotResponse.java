package com.parkeasy.backend.parking.dto;

import java.math.BigDecimal;

import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingType;

public record NearbyParkingLotResponse(
		Long id,
		String name,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		long distanceMeters,
		ParkingType parkingType,
		Integer totalSpaces,
		Integer baseTime,
		Integer baseFee,
		Boolean isFree,
		Boolean is24Hours,
		Integer availableSpaces,
		Boolean stale) {

	public static NearbyParkingLotResponse from(ParkingLot parkingLot, double distanceMeters,
			Integer availableSpaces, Boolean stale, ParkingOperationResponse operation) {
		OperationSummary summary = OperationSummary.from(operation);
		return new NearbyParkingLotResponse(
				parkingLot.getId(),
				parkingLot.getName(),
				parkingLot.getAddress(),
				parkingLot.getLatitude(),
				parkingLot.getLongitude(),
				Math.round(distanceMeters),
				parkingLot.getParkingType(),
				parkingLot.getTotalSpaces(),
				summary.baseTime(),
				summary.baseFee(),
				summary.free(),
				summary.open24Hours(),
				availableSpaces,
				stale);
	}
}
