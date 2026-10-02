package com.parkeasy.backend.external.parking;

record ParkingFacilityMappingResult(ParkingFacilityData data, String failureReason) {
	static ParkingFacilityMappingResult valid(ParkingFacilityData data) { return new ParkingFacilityMappingResult(data, null); }
	static ParkingFacilityMappingResult invalid(String reason) { return new ParkingFacilityMappingResult(null, reason); }
	boolean valid() { return data != null; }
}
