package com.parkeasy.backend.external.parking;

public record PublicDataFailure(PublicDataFailureCategory category, String rootErrorType) {
	public boolean transientFailure() {
		return category == PublicDataFailureCategory.TRANSIENT_NETWORK
				|| category == PublicDataFailureCategory.TRANSIENT_UPSTREAM;
	}
}
