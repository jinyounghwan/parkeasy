package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.dto.ParkingLotDetailResponse;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;
import com.parkeasy.backend.parking.service.ParkingLotService;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
@Transactional
class PublicParkingOperationSyncIntegrationTest {
	@Autowired ParkingLotRepository lotRepository;
	@Autowired ParkingOperationRepository operationRepository;
	@Autowired ParkingOperationUpsertService upsertService;
	@Autowired ParkingLotService parkingLotService;

	@Test
	void insertsThenUpdatesSingleOperationAndIncludesItInDetail() {
		ParkingLot lot = lotRepository.save(ParkingLot.builder().name("operation integration")
				.latitude(new BigDecimal("37.1")).longitude(new BigDecimal("127.1"))
				.dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId("INTEGRATION-OP-001").build());

		ParkingOperationSyncResult first = service(item("INTEGRATION-OP-001", "30", "1200")).sync(1);
		ParkingOperationSyncResult second = service(item("INTEGRATION-OP-001", "60", "2000")).sync(1);
		ParkingLotDetailResponse detail = parkingLotService.findById(lot.getId());

		assertEquals(1, first.insertedCount()); assertEquals(0, first.updatedCount());
		assertEquals(0, second.insertedCount()); assertEquals(1, second.updatedCount());
		assertEquals(1, operationRepository.countByParkingLotId(lot.getId()));
		assertNotNull(detail.operation());
		assertEquals(60, detail.operation().baseTime());
		assertEquals(2000, detail.operation().baseFee());
	}

	private PublicParkingOperationSyncService service(PublicParkingOperationItem item) {
		PublicParkingOperationClient client = (pageNo, rows) -> new PublicParkingOperationResponse(
				"1", List.of(item), "0", "1", "10", "SUCCESS");
		return new PublicParkingOperationSyncService(client, new ParkingOperationParser(), upsertService);
	}

	private PublicParkingOperationItem item(String id, String baseTime, String baseFee) {
		return new PublicParkingOperationItem(id, "0", new PublicParkingOperationTime("090000", "180000"),
				null, null, null, null, null, new PublicParkingOperationTime("000000", "000000"), null,
				new PublicParkingBasicInfo(baseTime, baseFee, "10", "0"),
				new PublicParkingFixedAmountInfo("10000", "100000"));
	}
}
