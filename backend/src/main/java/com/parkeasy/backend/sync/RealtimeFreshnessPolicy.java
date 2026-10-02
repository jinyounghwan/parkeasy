package com.parkeasy.backend.sync;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.parkeasy.backend.parking.domain.ParkingRealtime;

@Component
public class RealtimeFreshnessPolicy {
	private final ParkingSyncProperties properties;
	public RealtimeFreshnessPolicy(ParkingSyncProperties properties) { this.properties = properties; }

	public boolean isStale(ParkingRealtime realtime) {
		LocalDateTime receivedAt = realtime.getReceivedAt();
		return receivedAt == null || receivedAt.isBefore(
				LocalDateTime.now().minusMinutes(properties.realtime().staleAfterMinutes()));
	}
}
