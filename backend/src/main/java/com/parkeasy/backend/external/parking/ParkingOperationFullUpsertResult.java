package com.parkeasy.backend.external.parking;

record ParkingOperationFullUpsertResult(int matchedCount, int insertedCount,
		int updatedCount, int orphanCount) { }
