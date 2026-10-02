package com.parkeasy.backend.external.parking;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.parkeasy.backend.external.parking.ParkingRealtimeParser.ParsedRealtime;

@Service
public class PublicParkingRealtimeSyncService {
	public static final int NUM_OF_ROWS = 10;
	public static final int MAX_ALLOWED_PAGES = 100;
	private static final Logger log = LoggerFactory.getLogger(PublicParkingRealtimeSyncService.class);
	private final PublicParkingRealtimeClient client;
	private final ParkingRealtimeParser parser;
	private final ParkingRealtimeUpsertService upsertService;

	public PublicParkingRealtimeSyncService(PublicParkingRealtimeClient client,
			ParkingRealtimeParser parser, ParkingRealtimeUpsertService upsertService) {
		this.client = client; this.parser = parser; this.upsertService = upsertService;
	}

	public ParkingRealtimeSyncResult sync(int maxPages) {
		if (maxPages < 1 || maxPages > MAX_ALLOWED_PAGES) {
			throw new IllegalArgumentException("maxPages must be between 1 and " + MAX_ALLOWED_PAGES);
		}
		int fetched = 0, invalidSkipped = 0, duplicates = 0, fetchedPages = 0;
		Map<String, ParkingRealtimeCandidate> validByExternalId = new LinkedHashMap<>();
		Set<String> seen = new HashSet<>();
		for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
			PublicParkingRealtimeResponse response = client.fetchRealtime(pageNo, NUM_OF_ROWS);
			LocalDateTime receivedAt = LocalDateTime.now();
			fetchedPages++; fetched += response.safeItems().size();
			for (PublicParkingRealtimeItem item : response.safeItems()) {
				String externalId = StringUtils.hasText(item.externalId()) ? item.externalId().trim() : null;
				if (externalId != null && !seen.add(externalId)) duplicates++;
				if (externalId == null) { invalidSkipped++; continue; }
				ParsedRealtime parsed = parser.parse(item);
				if (!parsed.valid()) { invalidSkipped++; continue; }
				validByExternalId.put(externalId, new ParkingRealtimeCandidate(externalId,
						parsed.totalSpaces(), parsed.availableSpaces(), receivedAt));
			}
			if (response.safeItems().isEmpty() || isLastPage(response, pageNo)) break;
		}
		ParkingRealtimeUpsertResult upsert = upsertService.upsert(validByExternalId.values());
		ParkingRealtimeSyncResult result = new ParkingRealtimeSyncResult(maxPages, fetched, upsert.matchedCount(),
				upsert.insertedCount(), upsert.updatedCount(), invalidSkipped + upsert.skippedCount(), duplicates);
		log.info("Parking realtime sync completed: pages={}, fetched={}, matched={}, inserted={}, updated={}, skipped={}, duplicates={}",
				fetchedPages, fetched, result.matchedParkingLotCount(), result.insertedCount(), result.updatedCount(),
				result.skippedCount(), result.duplicateCount());
		return result;
	}

	private boolean isLastPage(PublicParkingRealtimeResponse response, int pageNo) {
		try { return (long) pageNo * NUM_OF_ROWS >= Long.parseLong(response.totalCount()); }
		catch (NumberFormatException | NullPointerException exception) { return false; }
	}
}
