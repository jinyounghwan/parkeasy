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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "parking_sync_skipped_page", uniqueConstraints =
		@UniqueConstraint(name = "uk_parking_sync_skipped_job_page", columnNames = {"job_id", "page_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSyncSkippedPage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "job_id", nullable = false) private Long jobId;
	@Column(name = "page_no", nullable = false) private int pageNo;
	@Enumerated(EnumType.STRING)
	@Column(name = "skip_reason", nullable = false, length = 50) private ParkingSyncSkipReason skipReason;
	@Column(name = "attempt_count", nullable = false) private int attemptCount;
	@Column(name = "observed_item_count", nullable = false) private int observedItemCount;
	@Column(name = "total_count", nullable = false) private long totalCount;
	@Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

	private ParkingSyncSkippedPage(long jobId, int pageNo, ParkingSyncSkipReason reason,
			int attemptCount, int observedItemCount, long totalCount) {
		this.jobId = jobId; this.pageNo = pageNo; this.skipReason = reason;
		this.attemptCount = attemptCount; this.observedItemCount = observedItemCount;
		this.totalCount = totalCount;
	}

	static ParkingSyncSkippedPage emptySuccess(long jobId, int pageNo, int attemptCount, long totalCount) {
		return new ParkingSyncSkippedPage(jobId, pageNo, ParkingSyncSkipReason.EMPTY_SUCCESS_PAGE,
				attemptCount, 0, totalCount);
	}

	@PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
