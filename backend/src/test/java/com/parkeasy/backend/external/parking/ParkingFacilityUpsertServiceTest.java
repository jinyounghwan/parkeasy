package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;

class ParkingFacilityUpsertServiceTest {
	@Test
	void batchUpsertsOneThousandExternalIdsWithOneExistingLookup() {
		ParkingLotRepository repository = mock(ParkingLotRepository.class);
		List<ParkingFacilityData> facilities = IntStream.range(0, 1000)
				.mapToObj(index -> data("batch-" + index, "name-" + index, "37.1"))
				.toList();
		List<ParkingLot> existing = IntStream.range(0, 500)
				.mapToObj(index -> ParkingLot.builder().name("old").latitude(new BigDecimal("37"))
						.longitude(new BigDecimal("127")).dataSource(DataSource.PUBLIC_DATA_PORTAL)
						.externalId("batch-" + index).build())
				.toList();
		when(repository.findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL),
				org.mockito.ArgumentMatchers.anyCollection())).thenReturn(existing);
		ParkingFacilityUpsertService service = new ParkingFacilityUpsertService(repository);

		ParkingFacilityUpsertResult result = service.upsert(facilities);

		assertEquals(500, result.insertedCount());
		assertEquals(500, result.updatedCount());
		verify(repository).findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL),
				org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == 1000));
		verify(repository).saveAll(org.mockito.ArgumentMatchers.argThat(rows ->
				((java.util.Collection<?>) rows).size() == 500));
		verify(repository, org.mockito.Mockito.never())
				.findByDataSourceAndExternalId(org.mockito.ArgumentMatchers.any(),
						org.mockito.ArgumentMatchers.any());
	}


	@Test
	void upsertOneReportsInsertAndUpdate() {
		ParkingLotRepository repository = mock(ParkingLotRepository.class);
		ParkingLot existing = ParkingLot.builder().name("old").latitude(new BigDecimal("37"))
				.longitude(new BigDecimal("127")).dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId("existing").build();
		when(repository.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "new")).thenReturn(Optional.empty());
		when(repository.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "existing")).thenReturn(Optional.of(existing));
		when(repository.save(org.mockito.ArgumentMatchers.any(ParkingLot.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		ParkingFacilityUpsertService service = new ParkingFacilityUpsertService(repository);

		ParkingFacilitySingleUpsertResult inserted = service.upsertOne(data("new", "new", "37.1"));
		ParkingFacilitySingleUpsertResult updated = service.upsertOne(data("existing", "changed", "37.2"));

		assertEquals(true, inserted.inserted()); assertEquals(false, inserted.updated());
		assertEquals(false, updated.inserted()); assertEquals(true, updated.updated());
		assertEquals("changed", existing.getName());
	}

	@Test
	void insertsNewAndUpdatesExistingFacility() {
		ParkingLotRepository repository = mock(ParkingLotRepository.class);
		ParkingLot existing = ParkingLot.builder().name("old").address("old")
				.latitude(new BigDecimal("37.0")).longitude(new BigDecimal("127.0"))
				.dataSource(DataSource.PUBLIC_DATA_PORTAL).externalId("existing").build();
		when(repository.findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL),
				org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(existing));
		ParkingFacilityUpsertService service = new ParkingFacilityUpsertService(repository);

		ParkingFacilityUpsertResult result = service.upsert(List.of(
				data("new", "new name", "37.1"), data("existing", "updated", "37.2")));

		assertEquals(1, result.insertedCount());
		assertEquals(1, result.updatedCount());
		assertEquals("updated", existing.getName());
		assertEquals(new BigDecimal("37.2"), existing.getLatitude());
		verify(repository).saveAll(org.mockito.ArgumentMatchers.argThat(rows ->
				rows.iterator().next().getExternalId().equals("new")));
	}

	private ParkingFacilityData data(String id, String name, String latitude) {
		return new ParkingFacilityData(id, name, "address", new BigDecimal(latitude),
				new BigDecimal("127.1"), 10);
	}
}
