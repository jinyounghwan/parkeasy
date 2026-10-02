package com.parkeasy.backend.external.parking;

public record PublicDataPageDiagnosticResult(
		int pageNo,
		long durationMs,
		boolean success,
		Integer status,
		String resultCode,
		int itemCount) {
}
