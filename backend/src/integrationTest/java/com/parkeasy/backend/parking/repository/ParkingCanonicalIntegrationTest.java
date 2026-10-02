package com.parkeasy.backend.parking.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import com.parkeasy.backend.parking.domain.*;
import com.parkeasy.backend.parking.service.*;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ParkingCanonicalIntegrationTest {
	@Autowired ParkingLotRepository lots;
	@Autowired ParkingOperationRepository operations;
	@Autowired ParkingRealtimeRepository realtime;
	@Autowired ParkingLotService service;
	@Autowired EntityManager em;
	private static final BigDecimal LAT = new BigDecimal("36.0"), LNG = new BigDecimal("126.0");

	@Test
	void canonicalFeesFiltersAndIndependentTotalsUseThreeQueriesWithoutNPlusOne() {
		ParkingLot paid = save("paid", ParkingType.PUBLIC, 99, 1, true);
		ParkingLot free = save("free", ParkingType.PRIVATE, 20, 9999, false);
		ParkingLot missing = save("missing", ParkingType.PUBLIC, 30, 0, true);
		operations.save(new ParkingOperation(paid, details(3000, false)));
		operations.save(new ParkingOperation(free, details(0, true)));
		realtime.save(new ParkingRealtime(paid, 2, 0, null, LocalDateTime.now()));
		em.flush(); em.clear();
		var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); statistics.clear();
		var nearby = service.findNearby(LAT, LNG, 2000, NearbyParkingLotFilter.none(), NearbyParkingLotSort.FEE, 100);
		assertEquals(3, statistics.getPrepareStatementCount());
		assertEquals(List.of(free.getId(), paid.getId(), missing.getId()), nearby.stream().map(l -> l.id()).toList());
		assertEquals(3000, nearby.get(1).baseFee()); assertEquals(30, nearby.get(1).baseTime());
		assertEquals(99, nearby.get(1).totalSpaces()); assertEquals(0, nearby.get(1).availableSpaces());
		assertNull(nearby.get(2).baseFee()); assertNull(nearby.get(2).isFree()); assertNull(nearby.get(2).is24Hours());
		assertNull(nearby.get(0).isFree()); assertNull(nearby.get(0).is24Hours());
		var detail = service.findById(paid.getId());
		assertEquals(nearby.get(1).baseFee(), detail.operation().baseFee());
		assertEquals(detail.baseFee(), detail.operation().baseFee()); assertEquals(nearby.get(1).baseTime(), detail.baseTime());
		assertEquals(99, detail.totalSpaces()); assertEquals(2, detail.realtime().totalSpaces());
		assertEquals(List.of(free.getId()), service.findNearby(LAT, LNG, 2000,
				new NearbyParkingLotFilter(ParkingType.PRIVATE, null, null, null), NearbyParkingLotSort.FEE, 1)
				.stream().map(l -> l.id()).toList());
		assertEquals(List.of(paid.getId()), service.findNearby(LAT, LNG, 2000,
				new NearbyParkingLotFilter(ParkingType.PUBLIC, null, null, true), NearbyParkingLotSort.FEE, 1)
				.stream().map(l -> l.id()).toList());
	}

	private ParkingLot save(String name, ParkingType type, int total, int legacyFee, boolean legacyFree) {
		return lots.save(ParkingLot.builder().name(name).latitude(LAT).longitude(LNG).totalSpaces(total)
				.parkingType(type).dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId("CANONICAL-"+name)
				.baseFee(legacyFee).baseTime(1).free(legacyFree).open24Hours(legacyFree).build());
	}

	private ParkingOperationDetails details(int fee, boolean allDay) {
		LocalTime open=LocalTime.MIDNIGHT, close=allDay?LocalTime.of(23,59,59):LocalTime.of(23,0);
		return new ParkingOperationDetails(0,30,fee,10,0,0,0,
				open,close,open,close,open,close,open,close,open,close,open,close,open,close,open,close);
	}
}
