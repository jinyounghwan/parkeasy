package com.parkeasy.backend.external.parking;

final class IncompletePublicDataPageException extends RuntimeException {
	private final int pageNo;
	private final int actualRows;
	private final boolean emptySuccessCandidate;
	private final long totalCount;
	private final long totalPages;

	IncompletePublicDataPageException(int pageNo, int actualRows, int expectedRows,
			boolean emptySuccessCandidate, long totalCount, long totalPages) {
		super("Public data page was incomplete: pageNo=" + pageNo
				+ ", actualRows=" + actualRows + ", expectedRows=" + expectedRows);
		this.pageNo = pageNo;
		this.actualRows = actualRows;
		this.emptySuccessCandidate = emptySuccessCandidate;
		this.totalCount = totalCount;
		this.totalPages = totalPages;
	}

	int pageNo() { return pageNo; }
	int actualRows() { return actualRows; }
	boolean emptySuccessCandidate() { return emptySuccessCandidate; }
	long totalCount() { return totalCount; }
	long totalPages() { return totalPages; }
}
