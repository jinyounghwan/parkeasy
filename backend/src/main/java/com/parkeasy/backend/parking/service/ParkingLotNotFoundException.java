package com.parkeasy.backend.parking.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ParkingLotNotFoundException extends RuntimeException {

	public ParkingLotNotFoundException(Long id) {
		super("Parking lot not found: " + id);
	}
}
