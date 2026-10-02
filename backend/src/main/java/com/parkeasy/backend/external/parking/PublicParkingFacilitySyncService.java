package com.parkeasy.backend.external.parking;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PublicParkingFacilitySyncService {
	public static final int DEFAULT_NUM_OF_ROWS = 10;
	public static final int DEFAULT_MAX_PAGES = 5;
	public static final int MAX_ALLOWED_PAGES = 100;

	private static final Logger log = LoggerFactory.getLogger(PublicParkingFacilitySyncService.class);

	private final PublicParkingClient client;
	private final ParkingRegionFilter regionFilter;
	private final ParkingFacilityUpsertService upsertService;
	private final ParkingFacilityMapper facilityMapper;

	public PublicParkingFacilitySyncService(PublicParkingClient client,
			ParkingRegionFilter regionFilter, ParkingFacilityUpsertService upsertService,
			ParkingFacilityMapper facilityMapper) {
		this.client = client;
		this.regionFilter = regionFilter;
		this.upsertService = upsertService;
		this.facilityMapper = facilityMapper;
	}

	public ParkingFacilitySyncResult sync(String sido, String sigungu, int maxPages) {
		if (!StringUtils.hasText(sido) || !StringUtils.hasText(sigungu)) {
			throw new IllegalArgumentException("sido and sigungu must not be blank");
		}
		if (maxPages < 1 || maxPages > MAX_ALLOWED_PAGES) {
			throw new IllegalArgumentException("maxPages must be between 1 and " + MAX_ALLOWED_PAGES);
		}

		int fetched = 0;
		int matched = 0;
		int skipped = 0;
		int duplicates = 0;
		int fetchedPages = 0;
		Map<String, ParkingFacilityData> validByExternalId = new LinkedHashMap<>();

		for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
			PublicParkingFacilityResponse response = client.fetchFacilities(pageNo, DEFAULT_NUM_OF_ROWS);
			fetchedPages++;
			fetched += response.safeItems().size();
			for (PublicParkingFacilityItem item : response.safeItems()) {
				if (!regionFilter.matches(item, sido, sigungu)) {
					continue;
				}
				matched++;
				ParkingFacilityMappingResult mapping = facilityMapper.map(item);
				if (!mapping.valid()) {
					skipped++;
					continue;
				}
				ParkingFacilityData data = mapping.data();
				if (validByExternalId.put(data.externalId(), data) != null) {
					duplicates++;
				}
			}
			if (response.safeItems().isEmpty() || isLastPage(response, pageNo)) {
				break;
			}
		}

		ParkingFacilityUpsertResult upsert = upsertService.upsert(validByExternalId.values());
		ParkingFacilitySyncResult result = new ParkingFacilitySyncResult(maxPages, fetched, matched,
				upsert.insertedCount(), upsert.updatedCount(), skipped, duplicates);
		log.info("Parking facility sync completed: pages={}, fetched={}, matched={}, inserted={}, updated={}, skipped={}, duplicates={}",
				fetchedPages, fetched, matched, result.insertedCount(), result.updatedCount(), skipped, duplicates);
		return result;
	}

	private boolean isLastPage(PublicParkingFacilityResponse response, int pageNo) {
		try {
			long totalCount = Long.parseLong(response.totalCount());
			return (long) pageNo * DEFAULT_NUM_OF_ROWS >= totalCount;
		} catch (NumberFormatException | NullPointerException exception) {
			return false;
		}
	}
}
