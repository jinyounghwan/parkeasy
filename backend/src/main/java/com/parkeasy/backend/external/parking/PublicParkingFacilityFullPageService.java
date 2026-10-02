package com.parkeasy.backend.external.parking;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicParkingFacilityFullPageService {
	private static final Logger log = LoggerFactory.getLogger(PublicParkingFacilityFullPageService.class);
	private final ParkingFacilityMapper mapper;
	private final ParkingFacilityUpsertService upsertService;
	private final ParkingSyncJobRepository jobRepository;
	private final ParkingSyncSkippedPageRepository skippedPageRepository;

	public PublicParkingFacilityFullPageService(ParkingFacilityMapper mapper,
			ParkingFacilityUpsertService upsertService, ParkingSyncJobRepository jobRepository,
			ParkingSyncSkippedPageRepository skippedPageRepository) {
		this.mapper = mapper; this.upsertService = upsertService; this.jobRepository = jobRepository;
		this.skippedPageRepository = skippedPageRepository;
	}

	@Transactional
	public ParkingFacilityFullPageResult process(long jobId, int pageNo,
			PublicParkingFacilityResponse response, long totalCount, long totalPages) {
		Map<String, ParkingFacilityData> validByExternalId = new LinkedHashMap<>();
		Map<String, Integer> occurrencesByExternalId = new LinkedHashMap<>();
		int skipped = 0;
		int duplicates = 0;
		for (PublicParkingFacilityItem item : response.safeItems()) {
			ParkingFacilityMappingResult mapping = mapper.map(item);
			if (!mapping.valid()) { skipped++; continue; }
			String externalId = mapping.data().externalId();
			occurrencesByExternalId.merge(externalId, 1, Integer::sum);
			if (validByExternalId.put(externalId, mapping.data()) != null) duplicates++;
		}
		occurrencesByExternalId.entrySet().stream().filter(entry -> entry.getValue() > 1).limit(5)
				.forEach(entry -> log.debug("FACILITY_DUPLICATE pageNo={} externalId={} occurrences={}",
						pageNo, entry.getKey(), entry.getValue()));
		log.info("FACILITY_PAGE_STATS pageNo={} raw={} uniqueExternalIds={} validUnique={} invalid={} duplicateOccurrences={}",
				pageNo, response.safeItems().size(), occurrencesByExternalId.size(),
				validByExternalId.size(), skipped, duplicates);
		ParkingFacilityUpsertResult upsert = upsertService.upsert(validByExternalId.values());
		ParkingSyncJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Sync job not found"));
		job.pageSucceeded(pageNo, totalCount, totalPages, response.safeItems().size(),
				upsert, skipped, duplicates);
		return new ParkingFacilityFullPageResult(response.safeItems().size(), upsert.insertedCount(),
				upsert.updatedCount(), skipped, duplicates, response.safeItems().isEmpty());
	}

	@Transactional
	public void skipEmptyPage(long jobId, int pageNo, int attemptCount,
			long totalCount, long totalPages) {
		ParkingSyncJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Sync job not found"));
		skippedPageRepository.save(ParkingSyncSkippedPage.emptySuccess(
				jobId, pageNo, attemptCount, totalCount));
		job.emptyPageSkipped(pageNo, totalCount, totalPages);
	}

	@Transactional
	public void bootstrapTrailingEmptyPages(long jobId, int threshold) {
		ParkingSyncJob job = jobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalStateException("Sync job not found"));
		int expectedPage = job.getLastSuccessPage();
		int count = 0;
		for (ParkingSyncSkippedPage skipped : skippedPageRepository
				.findByJobIdAndPageNoLessThanEqualOrderByPageNoDesc(jobId, expectedPage)) {
			if (skipped.getPageNo() != expectedPage
					|| skipped.getSkipReason() != ParkingSyncSkipReason.EMPTY_SUCCESS_PAGE) break;
			count++;
			expectedPage--;
		}
		if (count > 0) {
			job.bootstrapTrailingEmptyPages(count, expectedPage + 1,
					expectedPage > 0 ? expectedPage : null);
		}
	}
}
