package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
@Transactional
class PublicParkingFacilityExternalIdSyncIntegrationTest {
	@Autowired ParkingLotRepository repository;
	@Autowired ParkingFacilityUpsertService upsertService;

	@Test
	void insertsThenUpdatesAndKeepsSingleParkingLot() {
		String externalId = "INTEGRATION-TARGET-FACILITY";
		ParkingFacilityExternalIdSyncResult first = service(item(externalId, "first", "37.1")).syncByExternalId(externalId, 2, 100);
		ParkingFacilityExternalIdSyncResult second = service(item(externalId, "updated", "37.2")).syncByExternalId(externalId, 2, 100);

		assertTrue(first.inserted()); assertFalse(first.updated()); assertFalse(second.inserted()); assertTrue(second.updated());
		var rows = repository.findAll().stream().filter(lot -> externalId.equals(lot.getExternalId())).toList();
		assertEquals(1, rows.size()); assertEquals("updated", rows.get(0).getName());
		assertEquals(DataSource.PUBLIC_DATA_PORTAL, rows.get(0).getDataSource());
	}

	private PublicParkingFacilityExternalIdSyncService service(PublicParkingFacilityItem item) {
		PublicParkingClient client = (page, rows) -> new PublicParkingFacilityResponse(
				List.of(item), "1", "0", "1", String.valueOf(rows), "SUCCESS");
		return new PublicParkingFacilityExternalIdSyncService(
				new PublicParkingFacilityExternalIdFinder(client, new ParkingFacilityMapper()), upsertService);
	}

	private PublicParkingFacilityItem item(String id, String name, String latitude) {
		return new PublicParkingFacilityItem(id, name, "address", "sido", "sigungu", latitude, "127.1", "20");
	}
}
