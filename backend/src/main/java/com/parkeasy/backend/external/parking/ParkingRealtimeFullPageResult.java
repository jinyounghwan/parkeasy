package com.parkeasy.backend.external.parking;

record ParkingRealtimeFullPageResult(int processed, int inserted, int updated,
		int orphanCount, ParkingRealtimePageMetrics metrics) { }
