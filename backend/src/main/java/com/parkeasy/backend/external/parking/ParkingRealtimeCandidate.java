package com.parkeasy.backend.external.parking;

import java.time.LocalDateTime;

record ParkingRealtimeCandidate(String externalId, Integer totalSpaces,
		Integer availableSpaces, LocalDateTime receivedAt) {
}
