package com.parkeasy.backend.external.parking;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.parkeasy.backend.parking.domain.ParkingOperationDetails;

@Service
public class PublicParkingOperationSyncService {
	public static final int NUM_OF_ROWS = 10;
	public static final int DEFAULT_MAX_PAGES = 1;
	public static final int MAX_ALLOWED_PAGES = 100;
	private static final Logger log = LoggerFactory.getLogger(PublicParkingOperationSyncService.class);

	private final PublicParkingOperationClient client;
	private final ParkingOperationParser parser;
	private final ParkingOperationUpsertService upsertService;

	public PublicParkingOperationSyncService(PublicParkingOperationClient client,
			ParkingOperationParser parser, ParkingOperationUpsertService upsertService) {
		this.client = client; this.parser = parser; this.upsertService = upsertService;
	}

	public ParkingOperationSyncResult sync(int maxPages) {
		if (maxPages < 1 || maxPages > MAX_ALLOWED_PAGES) {
			throw new IllegalArgumentException("maxPages must be between 1 and " + MAX_ALLOWED_PAGES);
		}
		int fetched = 0, invalidSkipped = 0, duplicates = 0, fetchedPages = 0;
		Map<String, ParkingOperationCandidate> validByExternalId = new LinkedHashMap<>();
		Set<String> seenExternalIds = new HashSet<>();
		for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
			PublicParkingOperationResponse response = client.fetchOperations(pageNo, NUM_OF_ROWS);
			log.info("OPERATION_QUALITY pageNo={} scope=raw-dto {}", pageNo,
					OperationQualityMetrics.inspect(response.safeItems(), parser));
			fetchedPages++; fetched += response.safeItems().size();
			for (PublicParkingOperationItem item : response.safeItems()) {
				String externalId = StringUtils.hasText(item.externalId()) ? item.externalId().trim() : null;
				if (externalId != null && !seenExternalIds.add(externalId)) duplicates++;
				if (externalId == null) { invalidSkipped++; continue; }
				ParkingOperationDetails details = parser.parse(item);
				if (!parser.hasAnyValue(details)) { invalidSkipped++; continue; }
				validByExternalId.put(externalId, new ParkingOperationCandidate(externalId, details));
			}
			if (response.safeItems().isEmpty() || isLastPage(response, pageNo)) break;
		}
		ParkingOperationUpsertResult upsert = upsertService.upsert(validByExternalId.values());
		int skipped = invalidSkipped + upsert.skippedCount();
		ParkingOperationSyncResult result = new ParkingOperationSyncResult(maxPages, fetched,
				upsert.matchedCount(), upsert.insertedCount(), upsert.updatedCount(), skipped, duplicates);
		log.info("Parking operation sync completed: pages={}, fetched={}, matched={}, inserted={}, updated={}, skipped={}, duplicates={}",
				fetchedPages, fetched, result.matchedParkingLotCount(), result.insertedCount(),
				result.updatedCount(), result.skippedCount(), result.duplicateCount());
		return result;
	}

	private boolean isLastPage(PublicParkingOperationResponse response, int pageNo) {
		try { return (long) pageNo * NUM_OF_ROWS >= Long.parseLong(response.totalCount()); }
		catch (NumberFormatException | NullPointerException exception) { return false; }
	}
}
