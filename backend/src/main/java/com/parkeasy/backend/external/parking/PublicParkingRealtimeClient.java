package com.parkeasy.backend.external.parking;

public interface PublicParkingRealtimeClient {
	PublicParkingRealtimeResponse fetchRealtime(int pageNo, int numOfRows);
}
