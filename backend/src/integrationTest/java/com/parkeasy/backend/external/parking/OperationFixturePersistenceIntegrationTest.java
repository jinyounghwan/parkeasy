package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;
import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingOperationDetails;
import com.parkeasy.backend.parking.dto.ParkingOperationResponse;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest
@Transactional
class OperationFixturePersistenceIntegrationTest {
	@Autowired ParkingLotRepository lots;
	@Autowired ParkingOperationRepository operations;
	@Autowired ParkingOperationUpsertService upsert;
	@Autowired EntityManager em;

	@Test
	void lastValidSparseDuplicateInsertsNullAndBatchUpdateReplacesKnownFeeWithNull() {
		String id = "snapshot-replace-fixture";
		var lot = lots.save(ParkingLot.builder().name(id).latitude(new BigDecimal("35.1"))
				.longitude(new BigDecimal("126.1")).dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId(id).build());
		var parser = new ParkingOperationParser();
		var rich = item(id, "30", "3000");
		var sparse = item(id, "60", null);
		var empty = item(id, null, null);
		var response = new PublicParkingOperationResponse("1", java.util.List.of(rich, sparse, empty),
				"0", "3", "10", "SUCCESS");
		var result = new PublicParkingOperationSyncService((page, rows) -> response, parser, upsert).sync(1);
		assertEquals(1, result.insertedCount()); assertEquals(2, result.duplicateCount());
		em.flush(); em.clear();
		var inserted = operations.findByParkingLotId(lot.getId()).orElseThrow();
		assertEquals(60, inserted.getBaseTime()); assertNull(inserted.getBaseFee());
		upsert.upsert(java.util.List.of(new ParkingOperationCandidate(id, parser.parse(rich))));
		em.flush(); em.clear();
		assertEquals(3000, operations.findByParkingLotId(lot.getId()).orElseThrow().getBaseFee());
		upsert.upsertBatch(java.util.List.of(new ParkingOperationCandidate(id, parser.parse(sparse))));
		em.flush(); em.clear();
		var updated = operations.findByParkingLotId(lot.getId()).orElseThrow();
		assertEquals(60, updated.getBaseTime()); assertNull(updated.getBaseFee());
	}

	private PublicParkingOperationItem item(String id, String time, String fee) {
		return new PublicParkingOperationItem(id, null, null, null, null, null, null, null, null, null,
				new PublicParkingBasicInfo(time, fee, null, null), null);
	}

	@Test
	void syntheticRawResponseSurvivesParserUpsertDatabaseAndReloadIncludingZero() throws Exception {
		PublicParkingOperationResponse response;
		try (var input = getClass().getResourceAsStream("/fixtures/operation-cases.json")) {
			assertNotNull(input);
			response = JsonMapper.builder().build().readValue(input, PublicParkingOperationResponse.class);
		}
		var ids = new HashMap<String, Long>();
		for (var item : response.safeItems()) {
			var lot = lots.save(ParkingLot.builder().name(item.externalId())
					.latitude(new BigDecimal("35.1")).longitude(new BigDecimal("126.1"))
					.dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId(item.externalId()).build());
			ids.put(item.externalId(), lot.getId());
		}
		var parser = new ParkingOperationParser();
		var service = new PublicParkingOperationSyncService((page, rows) -> response, parser, upsert);
		var result = service.sync(1); // Synthetic client only: no HTTP transport.
		assertEquals(6, result.insertedCount()); assertEquals(3, result.skippedCount());
		em.flush(); em.clear();
		for (var item : response.safeItems()) {
			var expected = parser.parse(item);
			var stored = operations.findByParkingLotId(ids.get(item.externalId()));
			if (!parser.hasAnyValue(expected)) { assertTrue(stored.isEmpty()); continue; }
			var actual = ParkingOperationResponse.from(stored.orElseThrow());
			for (var field : ParkingOperationDetails.class.getRecordComponents()) {
				assertEquals(field.getAccessor().invoke(expected),
						ParkingOperationResponse.class.getMethod(field.getName()).invoke(actual),
						item.externalId() + ":" + field.getName());
			}
		}
	}
}
