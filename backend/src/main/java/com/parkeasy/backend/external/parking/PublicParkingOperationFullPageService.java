package com.parkeasy.backend.external.parking;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PublicParkingOperationFullPageService {
	private static final Logger log = LoggerFactory.getLogger(PublicParkingOperationFullPageService.class);
	private final ParkingOperationParser parser;
	private final ParkingOperationUpsertService upsertService;
	private final ParkingSyncJobRepository jobRepository;
	private final ParkingSyncSkippedPageRepository skippedPageRepository;

	public PublicParkingOperationFullPageService(ParkingOperationParser parser,
			ParkingOperationUpsertService upsertService, ParkingSyncJobRepository jobRepository,
			ParkingSyncSkippedPageRepository skippedPageRepository) {
		this.parser = parser; this.upsertService = upsertService;
		this.jobRepository = jobRepository; this.skippedPageRepository = skippedPageRepository;
	}

	@Transactional
	public ParkingOperationFullPageResult process(long jobId, int pageNo,
			PublicParkingOperationResponse response, long totalCount, long totalPages) {
		log.info("OPERATION_QUALITY jobId={} pageNo={} scope=raw-dto {}", jobId, pageNo,
				OperationQualityMetrics.inspect(response.safeItems(), parser));
		Map<String, ParkingOperationCandidate> valid = new LinkedHashMap<>();
		Map<String, Integer> occurrences = new LinkedHashMap<>();
		int missingExternalId = 0, invalidOperation = 0, duplicates = 0;
		for (PublicParkingOperationItem item : response.safeItems()) {
			String externalId = StringUtils.hasText(item.externalId()) ? item.externalId().trim() : null;
			if (externalId == null) { missingExternalId++; continue; }
			if (occurrences.merge(externalId, 1, Integer::sum) > 1) duplicates++;
			var details = parser.parse(item);
			if (!parser.hasAnyValue(details)) { invalidOperation++; continue; }
			valid.put(externalId, new ParkingOperationCandidate(externalId, details));
		}
		ParkingOperationFullUpsertResult upsert = upsertService.upsertBatch(valid.values());
		int skipped = missingExternalId + invalidOperation;
		ParkingSyncJob job = requiredJob(jobId);
		job.operationPageSucceeded(pageNo, totalCount, totalPages, response.safeItems().size(),
				upsert, skipped, duplicates, invalidOperation, missingExternalId);
		log.info("OPERATION_PAGE_STATS pageNo={} raw={} validUnique={} missingExternalId={} invalidOperation={} duplicates={} matched={} orphan={} inserted={} updated={}",
				pageNo, response.safeItems().size(), valid.size(), missingExternalId, invalidOperation,
				duplicates, upsert.matchedCount(), upsert.orphanCount(), upsert.insertedCount(), upsert.updatedCount());
		return new ParkingOperationFullPageResult(response.safeItems().size(), upsert.insertedCount(),
				upsert.updatedCount(), skipped, duplicates, upsert.orphanCount(), invalidOperation, missingExternalId);
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
		if (!ParkingSyncJob.OPERATION_FULL.equals(job.getSyncType())) {
			throw new IllegalArgumentException("Sync job is not an operation full sync job");
		}
		return job;
	}
}
