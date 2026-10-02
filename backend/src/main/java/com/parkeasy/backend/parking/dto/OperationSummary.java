package com.parkeasy.backend.parking.dto;

/** Fees come only from operation; the provider contract has no free/24-hour flags. */
public record OperationSummary(Integer baseTime, Integer baseFee, Boolean free, Boolean open24Hours) {
	public static OperationSummary from(ParkingOperationResponse operation) {
		// Zero fees and time pairs cannot establish provider classifications.
		return new OperationSummary(operation == null ? null : operation.baseTime(),
				operation == null ? null : operation.baseFee(), null, null);
	}
}
