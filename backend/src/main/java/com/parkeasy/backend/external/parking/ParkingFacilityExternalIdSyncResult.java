package com.parkeasy.backend.external.parking;

public record ParkingFacilityExternalIdSyncResult(
		String externalId, int requestedMaxPages, int searchedPages, boolean found,
		boolean inserted, boolean updated, int duplicateCount, Long parkingLotId,
		String name, String failureReason) { }
