package com.parkeasy.backend.external.parking;

public record ParkingRealtimeSyncResult(int requestedPages, int fetchedCount,
		int matchedParkingLotCount, int insertedCount, int updatedCount,
		int skippedCount, int duplicateCount) { }
