package com.parkeasy.backend.external.parking;

record ParkingRealtimePageMetrics(int skippedCount, int duplicateCount,
		int invalidRealtimeCount, int missingExternalIdCount, int zeroAvailableCount,
		int fullParkingCount, int missingTotalSpacesCount,
		int missingAvailableSpacesCount, int relationshipInvalidCount) { }
