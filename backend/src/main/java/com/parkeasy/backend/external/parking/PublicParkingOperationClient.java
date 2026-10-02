package com.parkeasy.backend.external.parking;

public interface PublicParkingOperationClient {
	PublicParkingOperationResponse fetchOperations(int pageNo, int numOfRows);
}
