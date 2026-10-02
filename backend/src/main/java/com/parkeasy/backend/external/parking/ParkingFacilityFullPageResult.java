package com.parkeasy.backend.external.parking;

record ParkingFacilityFullPageResult(int processed, int inserted, int updated,
		int skipped, int duplicates, boolean empty) {
}
