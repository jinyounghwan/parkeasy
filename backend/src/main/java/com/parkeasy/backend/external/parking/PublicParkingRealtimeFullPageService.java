package com.parkeasy.backend.external.parking;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.parkeasy.backend.external.parking.ParkingRealtimeParser.ParsedRealtime;

@Service
public class PublicParkingRealtimeFullPageService {
	private static final Logger log = LoggerFactory.getLogger(PublicParkingRealtimeFullPageService.class);
	private final ParkingRealtimeParser parser;
	private final ParkingRealtimeUpsertService upsertService;
	private final ParkingSyncJobRepository jobRepository;
	private final ParkingSyncSkippedPageRepository skippedPageRepository;

	public PublicParkingRealtimeFullPageService(ParkingRealtimeParser parser,
			ParkingRealtimeUpsertService upsertService, ParkingSyncJobRepository jobRepository,
			ParkingSyncSkippedPageRepository skippedPageRepository) {
		this.parser = parser; this.upsertService = upsertService;
		this.jobRepository = jobRepository; this.skippedPageRepository = skippedPageRepository;
	}

	@Transactional
	public ParkingRealtimeFullPageResult process(long jobId, int pageNo,
			PublicParkingRealtimeResponse response, long totalCount, long totalPages) {
		LocalDateTime receivedAt = LocalDateTime.now();
		Map<String, ParkingRealtimeCandidate> valid = new LinkedHashMap<>();
		Map<String, Integer> occurrences = new LinkedHashMap<>();
		int missingExternalId = 0, invalid = 0, duplicates = 0, zeroAvailable = 0,
				fullParking = 0, missingTotal = 0, missingAvailable = 0, relationshipInvalid = 0;
		for (PublicParkingRealtimeItem item : response.safeItems()) {
			String externalId = StringUtils.hasText(item.externalId()) ? item.externalId().trim() : null;
			if (externalId == null) { missingExternalId++; continue; }
			if (occurrences.merge(externalId, 1, Integer::sum) > 1) duplicates++;
			ParsedRealtime parsed = parser.parse(item);
			if (parsed.missingTotalSpaces()) missingTotal++;
			if (parsed.missingAvailableSpaces()) missingAvailable++;
			if (parsed.relationshipInvalid()) relationshipInvalid++;
			if (!parsed.valid()) { invalid++; continue; }
			if (parsed.availableSpaces() != null && parsed.availableSpaces() == 0) zeroAvailable++;
			if (parsed.totalSpaces() != null && parsed.availableSpaces() != null
					&& parsed.availableSpaces() == 0) fullParking++;
			valid.put(externalId, new ParkingRealtimeCandidate(externalId,
					parsed.totalSpaces(), parsed.availableSpaces(), receivedAt));
		}
		ParkingRealtimeFullUpsertResult upsert = upsertService.upsertBatch(valid.values());
		String firstExternalId = response.safeItems().stream().map(PublicParkingRealtimeItem::externalId)
				.filter(StringUtils::hasText).map(String::trim).findFirst().orElse(null);
		String lastExternalId = null;
		for (PublicParkingRealtimeItem item : response.safeItems()) {
			if (StringUtils.hasText(item.externalId())) lastExternalId = item.externalId().trim();
		}
		ParkingRealtimePageMetrics metrics = new ParkingRealtimePageMetrics(
				missingExternalId + invalid, duplicates, invalid, missingExternalId,
				zeroAvailable, fullParking, missingTotal, missingAvailable, relationshipInvalid);
		ParkingSyncJob job = requiredJob(jobId);
		job.realtimePageSucceeded(pageNo, totalCount, totalPages, response.safeItems().size(), upsert, metrics);
		log.info("REALTIME_PAGE_STATS pageNo={} firstExternalId={} lastExternalId={} raw={} validUnique={} missingExternalId={} invalid={} duplicates={} matched={} orphan={} inserted={} updated={} zeroAvailable={} zeroAvailableMatched={} zeroAvailableOrphan={} missingTotal={} missingAvailable={} relationshipInvalid={}",
				pageNo, firstExternalId, lastExternalId, response.safeItems().size(), valid.size(), missingExternalId, invalid, duplicates,
				upsert.matchedCount(), upsert.orphanCount(), upsert.insertedCount(), upsert.updatedCount(),
				zeroAvailable, upsert.zeroAvailableMatchedCount(), upsert.zeroAvailableOrphanCount(),
				missingTotal, missingAvailable, relationshipInvalid);
		return new ParkingRealtimeFullPageResult(response.safeItems().size(), upsert.insertedCount(),
				upsert.updatedCount(), upsert.orphanCount(), metrics);
	}

	@Transactional
	public void skipEmptyPage(long jobId, int pageNo, int observations, long totalCount, long totalPages) {
		ParkingSyncJob job = requiredJob(jobId);
		skippedPageRepository.save(ParkingSyncSkippedPage.emptySuccess(jobId, pageNo, observations, totalCount));
		job.emptyPageSkipped(pageNo, totalCount, totalPages);
	}

	@Transactional
	public void bootstrapTrailingEmptyPages(long jobId) {
		ParkingSyncJob job = requiredJob(jobId);
		int expected = job.getLastSuccessPage(), count = 0;
		for (ParkingSyncSkippedPage skipped : skippedPageRepository
				.findByJobIdAndPageNoLessThanEqualOrderByPageNoDesc(jobId, expected)) {
			if (skipped.getPageNo() != expected
					|| skipped.getSkipReason() != ParkingSyncSkipReason.EMPTY_SUCCESS_PAGE) break;
			count++; expected--;
		}
		if (count > 0) job.bootstrapTrailingEmptyPages(count, expected + 1, expected > 0 ? expected : null);
	}

	private ParkingSyncJob requiredJob(long jobId) {
		ParkingSyncJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Sync job not found"));
		if (!ParkingSyncJob.REALTIME_FULL.equals(job.getSyncType())) {
			throw new IllegalArgumentException("Sync job is not a realtime full sync job");
		}
		return job;
	}
}
