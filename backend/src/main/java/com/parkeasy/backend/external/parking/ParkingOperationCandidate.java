package com.parkeasy.backend.external.parking;

import com.parkeasy.backend.parking.domain.ParkingOperationDetails;

record ParkingOperationCandidate(String externalId, ParkingOperationDetails details) {
}
