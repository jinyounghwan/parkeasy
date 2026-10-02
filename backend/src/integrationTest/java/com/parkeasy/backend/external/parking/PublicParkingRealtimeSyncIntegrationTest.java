package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;
import com.parkeasy.backend.parking.service.ParkingLotService;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
@Transactional
class PublicParkingRealtimeSyncIntegrationTest {
	@Autowired ParkingLotRepository lotRepository;
	@Autowired ParkingRealtimeRepository realtimeRepository;
	@Autowired ParkingRealtimeUpsertService upsertService;
	@Autowired ParkingLotService lotService;

	@Test
	void insertsThenUpdatesSingleRealtimeAndReturnsNullableDetail() {
		ParkingLot withRealtime = lotRepository.save(lot("INTEGRATION-REALTIME-001"));
		ParkingLot withoutRealtime = lotRepository.save(lot("INTEGRATION-REALTIME-EMPTY"));

		ParkingRealtimeSyncResult first = service(item("INTEGRATION-REALTIME-001", "100", "40")).sync(1);
		ParkingRealtimeSyncResult second = service(item("INTEGRATION-REALTIME-001", "100", "35")).sync(1);
		ParkingLotDetailResponse detail = lotService.findById(withRealtime.getId());

		assertEquals(1, first.insertedCount()); assertEquals(1, second.updatedCount());
		assertEquals(1, realtimeRepository.countByParkingLotId(withRealtime.getId()));
		assertNotNull(detail.realtime()); assertEquals(35, detail.realtime().availableSpaces());
		assertNotNull(detail.realtime().receivedAt()); assertNull(detail.realtime().observedAt());
		assertNull(lotService.findById(withoutRealtime.getId()).realtime());
	}

	private PublicParkingRealtimeSyncService service(PublicParkingRealtimeItem item) {
		PublicParkingRealtimeClient client = (page, rows) -> new PublicParkingRealtimeResponse(
				"1", List.of(item), "0", "1", "10", "SUCCESS");
		return new PublicParkingRealtimeSyncService(client, new ParkingRealtimeParser(), upsertService);
	}

	private PublicParkingRealtimeItem item(String id, String total, String available) {
		return new PublicParkingRealtimeItem(id, total, available);
	}

	private ParkingLot lot(String externalId) {
		return ParkingLot.builder().name(externalId).latitude(new BigDecimal("37.1"))
				.longitude(new BigDecimal("127.1")).dataSource(DataSource.PUBLIC_DATA_PORTAL)
				.externalId(externalId).build();
	}
}
