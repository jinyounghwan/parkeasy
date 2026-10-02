package com.parkeasy.backend.external.parking;

public interface PublicParkingClient {
	PublicParkingFacilityResponse fetchFacilities(int pageNo, int numOfRows);
}
