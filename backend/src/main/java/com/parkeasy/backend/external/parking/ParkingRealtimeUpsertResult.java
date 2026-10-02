package com.parkeasy.backend.external.parking;

record ParkingRealtimeUpsertResult(int matchedCount, int insertedCount, int updatedCount, int skippedCount) { }
