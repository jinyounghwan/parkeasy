package com.parkeasy.backend.external.parking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record ParkingOperationFullSyncStatus(
		Long jobId, ParkingSyncJobStatus status, int lastProcessedPage, Integer lastDataPage,
		int nextPage, Integer currentPage, Long reportedTotalPages, Long reportedTotalCount,
		long processedCount, long insertedCount, long updatedCount, long skippedCount,
		long duplicateCount, long orphanCount, long invalidOperationCount,
		long missingExternalIdCount, int skippedPageCount, boolean hasDataGaps,
		ParkingSyncCompletionReason completionReason, PublicDataFailureCategory failureCategory,
		String rootErrorType, boolean workerRunning, int currentRetryBudget,
		int autoResumeAttempts, int consecutiveFailureCount, Integer currentEmptyPageNo,
		int currentEmptySuccessCount, int emptyPageSkipThreshold,
		int consecutiveEmptyPageCount, int trailingEmptyPageThreshold,
		Integer firstTrailingEmptyPage, BigDecimal progressPercent,
		List<ParkingSyncSkippedPageSummary> skippedPages) {

	static ParkingOperationFullSyncStatus from(ParkingSyncJob job, int maxAutoResumeAttempts,
			int emptyThreshold, int trailingThreshold, boolean workerRunning,
			List<ParkingSyncSkippedPageSummary> skippedPages) {
		BigDecimal progress = job.getTotalPages() == null || job.getTotalPages() == 0 ? null
				: BigDecimal.valueOf(job.getLastSuccessPage()).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(job.getTotalPages()), 2, RoundingMode.HALF_UP);
		return new ParkingOperationFullSyncStatus(job.getId(), job.getStatus(), job.getLastSuccessPage(),
				job.getLastDataPage(), job.getNextPage(), job.getCurrentPage(), job.getTotalPages(),
				job.getTotalCount(), job.getProcessedCount(), job.getInsertedCount(), job.getUpdatedCount(),
				job.getSkippedCount(), job.getDuplicateCount(), job.getOrphanCount(),
				job.getInvalidOperationCount(), job.getMissingExternalIdCount(), job.getSkippedPageCount(),
				job.getSkippedPageCount() > 0, job.getCompletionReason(), job.getFailureCategory(),
				job.getRootErrorType(), workerRunning,
				Math.max(0, maxAutoResumeAttempts - job.getAutoResumeAttempts()),
				job.getAutoResumeAttempts(), job.getConsecutiveFailureCount(), job.getCurrentEmptyPageNo(),
				job.getCurrentEmptySuccessCount(), emptyThreshold, job.getConsecutiveEmptyPageCount(),
				trailingThreshold, job.getFirstTrailingEmptyPage(), progress, List.copyOf(skippedPages));
	}
}
