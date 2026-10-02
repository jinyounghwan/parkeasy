package com.parkeasy.backend.external.parking;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PublicParkingFacilityExternalIdFinder {
	public static final int MAX_PAGES = 100;
	public static final int MAX_ROWS = 100;
	private final PublicParkingClient client;
	private final ParkingFacilityMapper mapper;

	public PublicParkingFacilityExternalIdFinder(PublicParkingClient client, ParkingFacilityMapper mapper) {
		this.client = client; this.mapper = mapper;
	}

	public ParkingFacilityExternalIdSearchResult find(String externalId, int maxPages, int numOfRows) {
		validate(externalId, maxPages, numOfRows);
		String target = externalId.trim();
		for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
			PublicParkingFacilityResponse response = client.fetchFacilities(pageNo, numOfRows);
			ParkingFacilityData lastValid = null;
			String lastFailureReason = null;
			int matches = 0;
			for (PublicParkingFacilityItem item : response.safeItems()) {
				if (!target.equals(item.externalId())) continue;
				matches++;
				ParkingFacilityMappingResult mapping = mapper.map(item);
				if (mapping.valid()) lastValid = mapping.data();
				else lastFailureReason = mapping.failureReason();
			}
			if (matches > 0) {
				return new ParkingFacilityExternalIdSearchResult(target, pageNo, true,
						matches - 1, lastValid, lastValid == null ? lastFailureReason : null);
			}
			if (response.safeItems().isEmpty() || isLastPage(response, pageNo, numOfRows)) {
				return new ParkingFacilityExternalIdSearchResult(target, pageNo, false, 0, null, "NOT_FOUND");
			}
		}
		return new ParkingFacilityExternalIdSearchResult(target, maxPages, false, 0, null, "NOT_FOUND");
	}

	private void validate(String externalId, int maxPages, int numOfRows) {
		if (!StringUtils.hasText(externalId)) throw new IllegalArgumentException("externalId must not be blank");
		if (externalId.length() > 100) throw new IllegalArgumentException("externalId must not exceed 100 characters");
		if (maxPages < 1 || maxPages > MAX_PAGES) throw new IllegalArgumentException("maxPages must be between 1 and 100");
		if (numOfRows < 1 || numOfRows > MAX_ROWS) throw new IllegalArgumentException("numOfRows must be between 1 and 100");
	}

	private boolean isLastPage(PublicParkingFacilityResponse response, int pageNo, int numOfRows) {
		try { return (long) pageNo * numOfRows >= Long.parseLong(response.totalCount()); }
		catch (NumberFormatException | NullPointerException exception) { return false; }
	}
}
