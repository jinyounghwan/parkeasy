package com.parkeasy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;
import com.parkeasy.backend.external.parking.ParkingSyncJobRepository;
import com.parkeasy.backend.external.parking.ParkingSyncSkippedPageRepository;

@SpringBootTest
class ParkEasyApplicationTests {

	@MockitoBean
	private ParkingLotRepository parkingLotRepository;

	@MockitoBean
	private ParkingOperationRepository parkingOperationRepository;

	@MockitoBean
	private ParkingRealtimeRepository parkingRealtimeRepository;

	@MockitoBean
	private ParkingSyncJobRepository parkingSyncJobRepository;

	@MockitoBean
	private ParkingSyncSkippedPageRepository parkingSyncSkippedPageRepository;

	@Test
	void contextLoads() {
	}

}
