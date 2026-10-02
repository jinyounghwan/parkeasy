package com.parkeasy.backend.external.parking;

import java.time.LocalDateTime;

public record ParkingSyncSkippedPageSummary(int pageNo, ParkingSyncSkipReason reason,
		int attemptCount, int observedItemCount, long totalCount, LocalDateTime createdAt) {
	static ParkingSyncSkippedPageSummary from(ParkingSyncSkippedPage page) {
		return new ParkingSyncSkippedPageSummary(page.getPageNo(), page.getSkipReason(),
				page.getAttemptCount(), page.getObservedItemCount(), page.getTotalCount(), page.getCreatedAt());
	}
}
