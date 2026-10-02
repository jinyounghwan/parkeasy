package com.parkeasy.backend.external.parking;

record ParkingRealtimeFullUpsertResult(int matchedCount, int insertedCount,
		int updatedCount, int orphanCount, int zeroAvailableMatchedCount,
		int zeroAvailableOrphanCount) { }
