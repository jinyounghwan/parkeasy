package com.parkeasy.backend.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingRealtime;

class RealtimeFreshnessPolicyTest {
	private final ParkingSyncProperties properties = new ParkingSyncProperties(
			new ParkingSyncProperties.Facility(false, "", 1, "", ""),
			new ParkingSyncProperties.Operation(false, "", 1),
			new ParkingSyncProperties.Realtime(false, 600000, 600000, 1, 30));
	private final RealtimeFreshnessPolicy policy = new RealtimeFreshnessPolicy(properties);

	@Test
	void marksRealtimeOlderThanConfiguredMinutesAsStale() {
		assertTrue(policy.isStale(realtime(LocalDateTime.now().minusMinutes(31))));
		assertFalse(policy.isStale(realtime(LocalDateTime.now().minusMinutes(5))));
	}

	private ParkingRealtime realtime(LocalDateTime receivedAt) {
		return new ParkingRealtime(org.mockito.Mockito.mock(ParkingLot.class), 10, 5, null, receivedAt);
	}
}
