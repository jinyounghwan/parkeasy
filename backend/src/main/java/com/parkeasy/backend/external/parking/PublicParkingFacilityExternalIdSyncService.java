package com.parkeasy.backend.external.parking;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PublicParkingFacilityExternalIdSyncService {
	private static final Logger log = LoggerFactory.getLogger(PublicParkingFacilityExternalIdSyncService.class);
	private final PublicParkingFacilityExternalIdFinder finder;
	private final ParkingFacilityUpsertService upsertService;

	public PublicParkingFacilityExternalIdSyncService(PublicParkingFacilityExternalIdFinder finder,
			ParkingFacilityUpsertService upsertService) {
		this.finder = finder; this.upsertService = upsertService;
	}

	public ParkingFacilityExternalIdSyncResult syncByExternalId(String externalId, int maxPages, int numOfRows) {
		Instant startedAt = Instant.now();
		ParkingFacilityExternalIdSearchResult search = finder.find(externalId, maxPages, numOfRows);
		ParkingFacilitySingleUpsertResult upsert = search.data() == null ? null : upsertService.upsertOne(search.data());
		ParkingFacilityExternalIdSyncResult result = new ParkingFacilityExternalIdSyncResult(
				search.externalId(), maxPages, search.searchedPages(), search.found(),
				upsert != null && upsert.inserted(), upsert != null && upsert.updated(), search.duplicateCount(),
				upsert == null ? null : upsert.parkingLotId(), search.data() == null ? null : search.data().name(),
				search.failureReason());
		log.info("Parking facility externalId sync completed: externalId={}, searchedPages={}, found={}, inserted={}, updated={}, durationMs={}",
				result.externalId(), result.searchedPages(), result.found(), result.inserted(), result.updated(),
				Duration.between(startedAt, Instant.now()).toMillis());
		return result;
	}
}
