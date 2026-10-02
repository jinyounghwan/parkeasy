package com.parkeasy.backend.external.parking;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "parking_sync_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSyncJob {
	public static final String FACILITY_FULL = "FACILITY_FULL";
	public static final String OPERATION_FULL = "OPERATION_FULL";
	public static final String REALTIME_FULL = "REALTIME_FULL";

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "sync_type", nullable = false, length = 50)
	private String syncType;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ParkingSyncJobStatus status;
	private LocalDateTime startedAt;
	private LocalDateTime endedAt;
	@Column(nullable = false) private int lastSuccessPage;
	@Column(nullable = false) private int nextPage;
	@Column(nullable = false) private int numOfRows;
	private Integer pageLimit;
	@Column(nullable = false) private int successfulPages;
	private Integer currentPage;
	@Column(nullable = false) private int autoResumeAttempts;
	@Column(nullable = false) private int consecutiveFailureCount;
	private Integer currentEmptyPageNo;
	@Column(nullable = false) private int currentEmptySuccessCount;
	@Column(nullable = false) private int consecutiveEmptyPageCount;
	private Integer firstTrailingEmptyPage;
	private Integer lastDataPage;
	@Enumerated(EnumType.STRING)
	@Column(length = 100) private ParkingSyncCompletionReason completionReason;
	private LocalDateTime lastFailureAt;
	private LocalDateTime lastResumeAt;
	private Long totalPages;
	private Long totalCount;
	@Column(nullable = false) private long processedCount;
	@Column(nullable = false) private long insertedCount;
	@Column(nullable = false) private long updatedCount;
	@Column(nullable = false) private long skippedCount;
	@Column(nullable = false) private long duplicateCount;
	@Column(nullable = false) private long orphanCount;
	@Column(nullable = false) private long invalidOperationCount;
	@Column(nullable = false) private long invalidRealtimeCount;
	@Column(nullable = false) private long missingExternalIdCount;
	@Column(nullable = false) private long zeroAvailableCount;
	@Column(nullable = false) private long fullParkingCount;
	@Column(nullable = false) private long missingTotalSpacesCount;
	@Column(nullable = false) private long missingAvailableSpacesCount;
	@Column(nullable = false) private long relationshipInvalidCount;
	@Column(nullable = false) private long realtimeMatchedCount;
	@Column(nullable = false) private long zeroAvailableMatchedCount;
	@Column(nullable = false) private long zeroAvailableOrphanCount;
	@Column(nullable = false) private int skippedPageCount;
	private Integer failedPage;
	@Column(length = 100) private String lastErrorType;
	@Column(length = 500) private String lastErrorMessage;
	@Enumerated(EnumType.STRING)
	@Column(length = 50) private PublicDataFailureCategory failureCategory;
	@Column(length = 100) private String rootErrorType;
	@Column(nullable = false, updatable = false) private LocalDateTime createdAt;
	@Column(nullable = false) private LocalDateTime updatedAt;

	private ParkingSyncJob(String syncType, int startPage, int numOfRows, Integer pageLimit) {
		this.syncType = syncType;
		this.status = ParkingSyncJobStatus.READY;
		this.lastSuccessPage = startPage - 1;
		this.nextPage = startPage;
		this.numOfRows = numOfRows;
		this.pageLimit = pageLimit;
	}

	public static ParkingSyncJob facilityFull(int startPage, int numOfRows, Integer pageLimit) {
		return new ParkingSyncJob(FACILITY_FULL, startPage, numOfRows, pageLimit);
	}

	public static ParkingSyncJob operationFull(int startPage, int numOfRows, Integer pageLimit) {
		return new ParkingSyncJob(OPERATION_FULL, startPage, numOfRows, pageLimit);
	}

	public static ParkingSyncJob realtimeFull(int startPage, int numOfRows, Integer pageLimit) {
		return new ParkingSyncJob(REALTIME_FULL, startPage, numOfRows, pageLimit);
	}

	void markRunning() {
		status = ParkingSyncJobStatus.RUNNING;
		if (startedAt == null) startedAt = LocalDateTime.now();
		endedAt = null; failedPage = null; lastErrorType = null; lastErrorMessage = null;
	}

	void prepareExplicitResume(Integer additionalPageLimit) {
		pageLimit = additionalPageLimit == null ? null : successfulPages + additionalPageLimit;
		autoResumeAttempts = 0;
		consecutiveFailureCount = 0;
		markRunning();
	}

	void markPageStarted(int pageNo) { currentPage = pageNo; }

	void recordPageFailure(int pageNo, PublicDataFailure failure) {
		failedPage = pageNo; nextPage = pageNo; currentPage = pageNo;
		consecutiveFailureCount++;
		lastFailureAt = LocalDateTime.now();
		failureCategory = failure.category();
		rootErrorType = truncate(failure.rootErrorType(), 100);
	}

	void recordEmptySuccessFailure(int pageNo, PublicDataFailure failure) {
		recordPageFailure(pageNo, failure);
		if (!Integer.valueOf(pageNo).equals(currentEmptyPageNo)) {
			currentEmptyPageNo = pageNo;
			currentEmptySuccessCount = 0;
		}
		currentEmptySuccessCount++;
	}

	void recordPartialPageFailure(int pageNo, PublicDataFailure failure) {
		recordPageFailure(pageNo, failure);
		resetEmptyObservation();
	}

	void markAutoResume() {
		autoResumeAttempts++;
		lastResumeAt = LocalDateTime.now();
	}

	void pageSucceeded(int pageNo, long totalCount, long totalPages, int processed,
			ParkingFacilityUpsertResult upsert, int skipped, int duplicates) {
		this.lastSuccessPage = pageNo;
		this.nextPage = pageNo + 1;
		this.totalCount = totalCount;
		this.totalPages = totalPages;
		this.processedCount += processed;
		this.insertedCount += upsert.insertedCount();
		this.updatedCount += upsert.updatedCount();
		this.skippedCount += skipped;
		this.duplicateCount += duplicates;
		this.failedPage = null;
		this.currentPage = null;
		this.successfulPages++;
		this.autoResumeAttempts = 0;
		this.consecutiveFailureCount = 0;
		resetEmptyObservation();
		this.consecutiveEmptyPageCount = 0;
		this.firstTrailingEmptyPage = null;
		this.lastDataPage = pageNo;
	}

	void emptyPageSkipped(int pageNo, long totalCount, long totalPages) {
		if (consecutiveEmptyPageCount == 0 || pageNo != lastSuccessPage + 1) {
			consecutiveEmptyPageCount = 1;
			firstTrailingEmptyPage = pageNo;
		} else {
			consecutiveEmptyPageCount++;
		}
		this.lastSuccessPage = pageNo;
		this.nextPage = pageNo + 1;
		this.totalCount = totalCount;
		this.totalPages = totalPages;
		this.skippedPageCount++;
		this.successfulPages++;
		this.failedPage = null;
		this.currentPage = null;
		this.autoResumeAttempts = 0;
		this.consecutiveFailureCount = 0;
		resetEmptyObservation();
	}

	void operationPageSucceeded(int pageNo, long totalCount, long totalPages, int processed,
			ParkingOperationFullUpsertResult upsert, int skipped, int duplicates,
			int invalidOperations, int missingExternalIds) {
		this.lastSuccessPage = pageNo;
		this.nextPage = pageNo + 1;
		this.totalCount = totalCount;
		this.totalPages = totalPages;
		this.processedCount += processed;
		this.insertedCount += upsert.insertedCount();
		this.updatedCount += upsert.updatedCount();
		this.skippedCount += skipped;
		this.duplicateCount += duplicates;
		this.orphanCount += upsert.orphanCount();
		this.invalidOperationCount += invalidOperations;
		this.missingExternalIdCount += missingExternalIds;
		this.failedPage = null;
		this.currentPage = null;
		this.successfulPages++;
		this.autoResumeAttempts = 0;
		this.consecutiveFailureCount = 0;
		resetEmptyObservation();
		this.consecutiveEmptyPageCount = 0;
		this.firstTrailingEmptyPage = null;
		this.lastDataPage = pageNo;
	}

	void realtimePageSucceeded(int pageNo, long totalCount, long totalPages, int processed,
			ParkingRealtimeFullUpsertResult upsert, ParkingRealtimePageMetrics metrics) {
		this.lastSuccessPage = pageNo; this.nextPage = pageNo + 1;
		this.totalCount = totalCount; this.totalPages = totalPages;
		this.processedCount += processed; this.insertedCount += upsert.insertedCount();
		this.updatedCount += upsert.updatedCount(); this.skippedCount += metrics.skippedCount();
		this.duplicateCount += metrics.duplicateCount(); this.orphanCount += upsert.orphanCount();
		this.invalidRealtimeCount += metrics.invalidRealtimeCount();
		this.missingExternalIdCount += metrics.missingExternalIdCount();
		this.zeroAvailableCount += metrics.zeroAvailableCount();
		this.fullParkingCount += metrics.fullParkingCount();
		this.missingTotalSpacesCount += metrics.missingTotalSpacesCount();
		this.missingAvailableSpacesCount += metrics.missingAvailableSpacesCount();
		this.relationshipInvalidCount += metrics.relationshipInvalidCount();
		this.realtimeMatchedCount += upsert.matchedCount();
		this.zeroAvailableMatchedCount += upsert.zeroAvailableMatchedCount();
		this.zeroAvailableOrphanCount += upsert.zeroAvailableOrphanCount();
		this.failedPage = null; this.currentPage = null; this.successfulPages++;
		this.autoResumeAttempts = 0; this.consecutiveFailureCount = 0;
		resetEmptyObservation(); this.consecutiveEmptyPageCount = 0;
		this.firstTrailingEmptyPage = null; this.lastDataPage = pageNo;
	}

	void markPaused() { status = ParkingSyncJobStatus.PAUSED; currentPage = null; endedAt = LocalDateTime.now(); }
	void markCompleted() {
		if (totalPages == null || lastSuccessPage < totalPages) {
			throw new IllegalStateException("Facility full sync cannot complete before totalPages");
		}
		status = skippedPageCount > 0
				? ParkingSyncJobStatus.COMPLETED_WITH_GAPS : ParkingSyncJobStatus.COMPLETED;
		completionReason = ParkingSyncCompletionReason.FULLY_PROCESSED;
		currentPage = null; endedAt = LocalDateTime.now();
	}

	void markCompletedForTrailingEmptyPages() {
		if (consecutiveEmptyPageCount <= 0 || firstTrailingEmptyPage == null) {
			throw new IllegalStateException("Trailing empty completion requires observed empty pages");
		}
		status = ParkingSyncJobStatus.COMPLETED_WITH_GAPS;
		completionReason = ParkingSyncCompletionReason.PROVIDER_TRAILING_EMPTY_PAGES;
		currentPage = null;
		endedAt = LocalDateTime.now();
	}

	void bootstrapTrailingEmptyPages(int count, int firstPage, Integer inferredLastDataPage) {
		if (count <= consecutiveEmptyPageCount) return;
		consecutiveEmptyPageCount = count;
		firstTrailingEmptyPage = firstPage;
		if (lastDataPage == null) lastDataPage = inferredLastDataPage;
	}

	void repairPrematureCompletion() {
		if (status != ParkingSyncJobStatus.COMPLETED || totalPages == null || lastSuccessPage >= totalPages) {
			throw new IllegalStateException("Sync job is not a premature completion");
		}
		status = ParkingSyncJobStatus.PAUSED;
		nextPage = lastSuccessPage + 1;
		currentPage = null;
		endedAt = null;
	}
	void markFailed(int pageNo, PublicDataFailure failure, String safeMessage) {
		status = ParkingSyncJobStatus.FAILED; failedPage = pageNo; nextPage = pageNo;
		currentPage = null;
		lastErrorType = truncate(failure.rootErrorType(), 100); lastErrorMessage = truncate(safeMessage, 500);
		failureCategory = failure.category(); rootErrorType = truncate(failure.rootErrorType(), 100);
		endedAt = LocalDateTime.now();
	}

	boolean reachedPageLimit() { return pageLimit != null && successfulPages >= pageLimit; }

	private void resetEmptyObservation() {
		currentEmptyPageNo = null;
		currentEmptySuccessCount = 0;
	}

	private String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	@PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
	@PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
