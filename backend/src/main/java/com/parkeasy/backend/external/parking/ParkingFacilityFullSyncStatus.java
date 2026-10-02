package com.parkeasy.backend.external.parking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public record ParkingFacilityFullSyncStatus(
		Long jobId,
		ParkingSyncJobStatus status,
		int lastSuccessPage,
		int lastProcessedPage,
		Integer lastDataPage,
		int nextPage,
		Integer currentPage,
		Long totalPages,
		Long totalCount,
		Long reportedTotalPages,
		Long reportedTotalCount,
		Long reportedRemainingCount,
		long processedCount,
		long insertedCount,
		long updatedCount,
		long skippedCount,
		long duplicateCount,
		int skippedPageCount,
		boolean hasDataGaps,
		List<ParkingSyncSkippedPageSummary> skippedPages,
		Integer failedPage,
		String lastErrorType,
		String lastErrorMessage,
		PublicDataFailureCategory failureCategory,
		String rootErrorType,
		int autoResumeAttempts,
		int consecutiveFailureCount,
		Integer currentEmptyPageNo,
		int currentEmptySuccessCount,
		int emptyPageSkipThreshold,
		int consecutiveEmptyPageCount,
		int trailingEmptyPageThreshold,
		Integer firstTrailingEmptyPage,
		ParkingSyncCompletionReason completionReason,
		int currentRetryBudget,
		int maxAutoResumeAttempts,
		boolean workerRunning,
		LocalDateTime lastFailureAt,
		LocalDateTime lastResumeAt,
		BigDecimal progressPercent,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		LocalDateTime updatedAt) {

	static ParkingFacilityFullSyncStatus from(ParkingSyncJob job,
			int maxAutoResumeAttempts, int emptyPageSkipThreshold,
			int trailingEmptyPageThreshold, boolean workerRunning,
			List<ParkingSyncSkippedPageSummary> skippedPages) {
		BigDecimal progress = job.getTotalPages() == null || job.getTotalPages() == 0 ? null
				: BigDecimal.valueOf(job.getLastSuccessPage()).multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(job.getTotalPages()), 2, RoundingMode.HALF_UP);
		Long reportedRemaining = job.getTotalCount() == null ? null
				: Math.max(0, job.getTotalCount() - job.getProcessedCount());
		return new ParkingFacilityFullSyncStatus(job.getId(), job.getStatus(), job.getLastSuccessPage(),
				job.getLastSuccessPage(), job.getLastDataPage(), job.getNextPage(), job.getCurrentPage(),
				job.getTotalPages(), job.getTotalCount(), job.getTotalPages(), job.getTotalCount(),
				reportedRemaining, job.getProcessedCount(),
				job.getInsertedCount(), job.getUpdatedCount(), job.getSkippedCount(),
				job.getDuplicateCount(), job.getSkippedPageCount(), job.getSkippedPageCount() > 0,
				List.copyOf(skippedPages), job.getFailedPage(), job.getLastErrorType(),
				job.getLastErrorMessage(), job.getFailureCategory(), job.getRootErrorType(),
				job.getAutoResumeAttempts(), job.getConsecutiveFailureCount(),
				job.getCurrentEmptyPageNo(), job.getCurrentEmptySuccessCount(), emptyPageSkipThreshold,
				job.getConsecutiveEmptyPageCount(), trailingEmptyPageThreshold,
				job.getFirstTrailingEmptyPage(), job.getCompletionReason(),
				Math.max(0, maxAutoResumeAttempts - job.getAutoResumeAttempts()),
				maxAutoResumeAttempts, workerRunning, job.getLastFailureAt(), job.getLastResumeAt(),
				progress, job.getStartedAt(), job.getEndedAt(), job.getUpdatedAt());
	}
}
