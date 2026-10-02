package com.parkeasy.backend.external.parking;

record ParkingFacilityExternalIdSearchResult(String externalId, int searchedPages, boolean found,
		int duplicateCount, ParkingFacilityData data, String failureReason) { }
