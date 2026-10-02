package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
@Transactional
class PublicParkingFacilitySyncIntegrationTest {

	@Autowired
	private ParkingLotRepository repository;

	@Autowired
	private ParkingFacilityUpsertService upsertService;

	@Test
	void insertsThenUpdatesSameExternalIdWithoutDuplicateRow() {
		String externalId = "INTEGRATION-FACILITY-001";
		PublicParkingFacilitySyncService firstSync = service(response(
				item(externalId, "first", "37.1000000")));
		ParkingFacilitySyncResult first = firstSync.sync("경기도", "수원", 1);

		PublicParkingFacilitySyncService secondSync = service(response(
				item(externalId, "stale duplicate", "37.2000000"),
				item(externalId, "latest", "37.3000000")));
		ParkingFacilitySyncResult second = secondSync.sync("경기도", "수원", 1);

		List<ParkingLot> rows = repository.findAll().stream()
				.filter(parkingLot -> externalId.equals(parkingLot.getExternalId()))
				.toList();
		assertEquals(1, first.insertedCount());
		assertEquals(1, second.updatedCount());
		assertEquals(1, second.duplicateCount());
		assertEquals(1, rows.size());
		assertEquals("latest", rows.get(0).getName());
		assertEquals(DataSource.PUBLIC_DATA_PORTAL, rows.get(0).getDataSource());
	}

	private PublicParkingFacilitySyncService service(PublicParkingFacilityResponse response) {
		PublicParkingClient client = (pageNo, numOfRows) -> response;
		return new PublicParkingFacilitySyncService(client, new ParkingRegionFilter(), upsertService,
				new ParkingFacilityMapper());
	}

	private PublicParkingFacilityResponse response(PublicParkingFacilityItem... items) {
		return new PublicParkingFacilityResponse(List.of(items), "1", "0",
				Integer.toString(items.length), "100", "SUCCESS");
	}

	private PublicParkingFacilityItem item(String id, String name, String latitude) {
		return new PublicParkingFacilityItem(id, name, "경기도 수원시", "경기도", "수원시 팔달구",
				latitude, "127.1000000", "25");
	}
}
