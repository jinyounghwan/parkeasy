package com.parkeasy.backend.parking.service;

import com.parkeasy.backend.parking.domain.ParkingType;

public record NearbyParkingLotFilter(
		ParkingType parkingType,
		Boolean free,
		Boolean open24Hours,
		Boolean realtimeAvailable) {

	public static NearbyParkingLotFilter none() {
		return new NearbyParkingLotFilter(null, null, null, null);
	}
}
