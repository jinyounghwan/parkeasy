package com.parkeasy.backend.parking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
class ParkingLotRepositoryIntegrationTest {
	@Autowired ParkingLotRepository repository;

	@Test
	void nearbyBoundingBoxExcludesZeroCoordinatesAndDevelopmentSamples() {
		repository.save(lot("PUBLIC-1", "37.2636", "127.0286"));
		repository.save(lot("SAMPLE-001", "37.2636", "127.0286"));
		repository.save(lot("ZERO", "0", "0"));

		var suwon = repository.findWithinBoundingBox(new BigDecimal("37.2"),
				new BigDecimal("37.3"), new BigDecimal("127.0"), new BigDecimal("127.1"));
		var zero = repository.findWithinBoundingBox(new BigDecimal("-1"),
				BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ONE);

		assertEquals(1, suwon.size());
		assertEquals("PUBLIC-1", suwon.get(0).getExternalId());
		assertEquals(0, zero.size());
	}

	private ParkingLot lot(String externalId, String latitude, String longitude) {
		return ParkingLot.builder().name(externalId).latitude(new BigDecimal(latitude))
				.longitude(new BigDecimal(longitude)).dataSource(DataSource.PUBLIC_DATA_PORTAL)
				.externalId(externalId).build();
	}
}
