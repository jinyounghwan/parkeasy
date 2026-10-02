package com.parkeasy.backend.external.parking;

record ParkingOperationUpsertResult(int matchedCount, int insertedCount, int updatedCount, int skippedCount) {
}
