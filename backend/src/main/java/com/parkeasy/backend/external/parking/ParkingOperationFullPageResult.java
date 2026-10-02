package com.parkeasy.backend.external.parking;

record ParkingOperationFullPageResult(int processed, int inserted, int updated,
		int skipped, int duplicates, int orphanCount, int invalidOperationCount,
		int missingExternalIdCount) { }
