package com.parkeasy.backend.external.parking;

public enum PublicDataFailureCategory {
	TRANSIENT_NETWORK,
	TRANSIENT_UPSTREAM,
	PERMANENT_PARSE,
	PERMANENT_CLIENT,
	UNKNOWN
}
