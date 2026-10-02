package com.parkeasy.backend.external.parking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record ParkingRealtimeFullSyncStatus(
		Long jobId, ParkingSyncJobStatus status, int lastProcessedPage, Integer lastDataPage,
		int nextPage, Integer currentPage, Long reportedTotalPages, Long reportedTotalCount,
		long processedCount, long insertedCount, long updatedCount, long skippedCount,
		long duplicateCount, long orphanCount, long invalidRealtimeCount,
		long missingExternalIdCount, long zeroAvailableCount, long fullParkingCount,
		long missingTotalSpacesCount, long missingAvailableSpacesCount,
		long relationshipInvalidCount, long matchedCount,
		BigDecimal exactMatchRate, long zeroAvailableMatchedCount,
		long zeroAvailableOrphanCount, int skippedPageCount, boolean hasDataGaps,
		ParkingSyncCompletionReason completionReason, PublicDataFailureCategory failureCategory,
		String rootErrorType, boolean workerRunning, int currentRetryBudget,
		int autoResumeAttempts, int consecutiveFailureCount, Integer currentEmptyPageNo,
		int currentEmptySuccessCount, int emptyPageSkipThreshold,
		int consecutiveEmptyPageCount, int trailingEmptyPageThreshold,
		Integer firstTrailingEmptyPage, BigDecimal progressPercent,
		List<ParkingSyncSkippedPageSummary> skippedPages) {

	static ParkingRealtimeFullSyncStatus from(ParkingSyncJob job, int maxAutoResumeAttempts,
			int emptyThreshold, int trailingThreshold, boolean workerRunning,
			List<ParkingSyncSkippedPageSummary> skippedPages) {
		BigDecimal progress = job.getTotalPages() == null || job.getTotalPages() == 0 ? null
				: BigDecimal.valueOf(job.getLastSuccessPage()).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(job.getTotalPages()), 2, RoundingMode.HALF_UP);
		long matchDenominator = job.getRealtimeMatchedCount() + job.getOrphanCount();
		BigDecimal exactMatchRate = matchDenominator == 0 ? null
				: BigDecimal.valueOf(job.getRealtimeMatchedCount()).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(matchDenominator), 2, RoundingMode.HALF_UP);
		return new ParkingRealtimeFullSyncStatus(job.getId(), job.getStatus(), job.getLastSuccessPage(),
				job.getLastDataPage(), job.getNextPage(), job.getCurrentPage(), job.getTotalPages(),
				job.getTotalCount(), job.getProcessedCount(), job.getInsertedCount(), job.getUpdatedCount(),
				job.getSkippedCount(), job.getDuplicateCount(), job.getOrphanCount(),
				job.getInvalidRealtimeCount(), job.getMissingExternalIdCount(), job.getZeroAvailableCount(),
				job.getFullParkingCount(), job.getMissingTotalSpacesCount(),
				job.getMissingAvailableSpacesCount(), job.getRelationshipInvalidCount(),
				job.getRealtimeMatchedCount(), exactMatchRate, job.getZeroAvailableMatchedCount(),
				job.getZeroAvailableOrphanCount(),
				job.getSkippedPageCount(), job.getSkippedPageCount() > 0, job.getCompletionReason(),
				job.getFailureCategory(), job.getRootErrorType(), workerRunning,
				Math.max(0, maxAutoResumeAttempts - job.getAutoResumeAttempts()),
				job.getAutoResumeAttempts(), job.getConsecutiveFailureCount(), job.getCurrentEmptyPageNo(),
				job.getCurrentEmptySuccessCount(), emptyThreshold, job.getConsecutiveEmptyPageCount(),
				trailingThreshold, job.getFirstTrailingEmptyPage(), progress, List.copyOf(skippedPages));
	}
}
