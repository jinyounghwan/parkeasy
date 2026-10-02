package com.parkeasy.backend.external.parking;

public record ParkingFacilitySyncResult(
		int requestedPages,
		int fetchedCount,
		int matchedCount,
		int insertedCount,
		int updatedCount,
		int skippedCount,
		int duplicateCount) {
}
