package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingOperation;
import com.parkeasy.backend.parking.domain.ParkingOperationDetails;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;

class ParkingOperationUpsertServiceTest {
	@Test
	void insertsUpdatesAndSkipsUnmatchedParkingLot() {
		ParkingLotRepository lotRepository = mock(ParkingLotRepository.class);
		ParkingOperationRepository operationRepository = mock(ParkingOperationRepository.class);
		ParkingLot insertLot = mock(ParkingLot.class); when(insertLot.getId()).thenReturn(1L);
		ParkingLot updateLot = mock(ParkingLot.class); when(updateLot.getId()).thenReturn(2L);
		ParkingOperation existing = new ParkingOperation(updateLot, details(10));
		when(lotRepository.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "insert")).thenReturn(Optional.of(insertLot));
		when(lotRepository.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "update")).thenReturn(Optional.of(updateLot));
		when(lotRepository.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "missing")).thenReturn(Optional.empty());
		when(operationRepository.findByParkingLotId(1L)).thenReturn(Optional.empty());
		when(operationRepository.findByParkingLotId(2L)).thenReturn(Optional.of(existing));
		ParkingOperationUpsertService service = new ParkingOperationUpsertService(lotRepository, operationRepository);

		ParkingOperationUpsertResult result = service.upsert(List.of(
				new ParkingOperationCandidate("insert", details(20)), new ParkingOperationCandidate("update", details(30)),
				new ParkingOperationCandidate("missing", details(40))));

		assertEquals(2, result.matchedCount()); assertEquals(1, result.insertedCount());
		assertEquals(1, result.updatedCount()); assertEquals(1, result.skippedCount());
		assertEquals(30, existing.getBaseTime());
		verify(operationRepository).save(org.mockito.ArgumentMatchers.any(ParkingOperation.class));
	}

	@Test
	void batchLooksUpLotsAndExistingOperationsOnce() {
		ParkingLotRepository lotRepository = mock(ParkingLotRepository.class);
		ParkingOperationRepository operationRepository = mock(ParkingOperationRepository.class);
		ParkingLot insertLot = mock(ParkingLot.class); when(insertLot.getId()).thenReturn(1L); when(insertLot.getExternalId()).thenReturn("insert");
		ParkingLot updateLot = mock(ParkingLot.class); when(updateLot.getId()).thenReturn(2L); when(updateLot.getExternalId()).thenReturn("update");
		ParkingOperation existing = new ParkingOperation(updateLot, details(10));
		when(lotRepository.findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL), org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(insertLot, updateLot));
		when(operationRepository.findByParkingLotIdIn(org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(existing));
		ParkingOperationUpsertService service = new ParkingOperationUpsertService(lotRepository, operationRepository);

		ParkingOperationFullUpsertResult result = service.upsertBatch(List.of(
				new ParkingOperationCandidate("insert", details(20)),
				new ParkingOperationCandidate("update", details(30)),
				new ParkingOperationCandidate("orphan", details(40))));

		assertEquals(2, result.matchedCount()); assertEquals(1, result.insertedCount());
		assertEquals(1, result.updatedCount()); assertEquals(1, result.orphanCount());
		assertEquals(30, existing.getBaseTime());
		verify(lotRepository, times(1)).findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL), org.mockito.ArgumentMatchers.anyCollection());
		verify(operationRepository, times(1)).findByParkingLotIdIn(org.mockito.ArgumentMatchers.anyCollection());
		verify(operationRepository).saveAll(org.mockito.ArgumentMatchers.anyCollection());
	}

	private ParkingOperationDetails details(int baseTime) {
		return new ParkingOperationDetails(null, baseTime, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}
}
