package com.parkeasy.backend.parking.dto;

import java.time.LocalDateTime;

import com.parkeasy.backend.parking.domain.ParkingRealtime;

public record ParkingRealtimeResponse(Integer totalSpaces, Integer availableSpaces,
		LocalDateTime observedAt, LocalDateTime receivedAt, boolean stale) {
	public static ParkingRealtimeResponse from(ParkingRealtime realtime, boolean stale) {
		return realtime == null ? null : new ParkingRealtimeResponse(realtime.getTotalSpaces(),
				realtime.getAvailableSpaces(), realtime.getObservedAt(), realtime.getReceivedAt(), stale);
	}
}
