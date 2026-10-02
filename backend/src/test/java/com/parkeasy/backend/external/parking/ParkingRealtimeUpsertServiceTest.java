package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingRealtime;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;

class ParkingRealtimeUpsertServiceTest {
	@Test
	void insertsUpdatesAndSkipsUnmatchedLot() {
		ParkingLotRepository lots = mock(ParkingLotRepository.class);
		ParkingRealtimeRepository realtime = mock(ParkingRealtimeRepository.class);
		ParkingLot insertLot = mock(ParkingLot.class); when(insertLot.getId()).thenReturn(1L);
		ParkingLot updateLot = mock(ParkingLot.class); when(updateLot.getId()).thenReturn(2L);
		ParkingRealtime existing = new ParkingRealtime(updateLot, 10, 5, null, LocalDateTime.now().minusMinutes(1));
		when(lots.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "insert")).thenReturn(Optional.of(insertLot));
		when(lots.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "update")).thenReturn(Optional.of(updateLot));
		when(lots.findByDataSourceAndExternalId(DataSource.PUBLIC_DATA_PORTAL, "missing")).thenReturn(Optional.empty());
		when(realtime.findByParkingLotId(1L)).thenReturn(Optional.empty());
		when(realtime.findByParkingLotId(2L)).thenReturn(Optional.of(existing));
		ParkingRealtimeUpsertService service = new ParkingRealtimeUpsertService(lots, realtime);
		LocalDateTime receivedAt = LocalDateTime.now();

		ParkingRealtimeUpsertResult result = service.upsert(List.of(
				new ParkingRealtimeCandidate("insert", 20, 8, receivedAt),
				new ParkingRealtimeCandidate("update", 30, 12, receivedAt),
				new ParkingRealtimeCandidate("missing", 40, 20, receivedAt)));

		assertEquals(2, result.matchedCount()); assertEquals(1, result.insertedCount());
		assertEquals(1, result.updatedCount()); assertEquals(1, result.skippedCount());
		assertEquals(30, existing.getTotalSpaces()); assertEquals(12, existing.getAvailableSpaces());
		assertEquals(receivedAt, existing.getReceivedAt());
		verify(realtime).save(org.mockito.ArgumentMatchers.any(ParkingRealtime.class));
	}

	@Test
	void batchMatchesAndUpdatesReceivedAtWithoutNPlusOne() {
		ParkingLotRepository lots = mock(ParkingLotRepository.class);
		ParkingRealtimeRepository realtime = mock(ParkingRealtimeRepository.class);
		ParkingLot insertLot = mock(ParkingLot.class); when(insertLot.getId()).thenReturn(1L); when(insertLot.getExternalId()).thenReturn("insert");
		ParkingLot updateLot = mock(ParkingLot.class); when(updateLot.getId()).thenReturn(2L); when(updateLot.getExternalId()).thenReturn("update");
		ParkingRealtime existing = new ParkingRealtime(updateLot, 10, 5, null, LocalDateTime.now().minusHours(1));
		when(lots.findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL), org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(insertLot, updateLot));
		when(realtime.findByParkingLotIdIn(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(existing));
		ParkingRealtimeUpsertService service = new ParkingRealtimeUpsertService(lots, realtime);
		LocalDateTime receivedAt = LocalDateTime.now();

		ParkingRealtimeFullUpsertResult result = service.upsertBatch(List.of(
				new ParkingRealtimeCandidate("insert", 20, 0, receivedAt),
				new ParkingRealtimeCandidate("update", 30, 12, receivedAt),
				new ParkingRealtimeCandidate("orphan", 40, 20, receivedAt)));

		assertEquals(2, result.matchedCount()); assertEquals(1, result.insertedCount());
		assertEquals(1, result.updatedCount()); assertEquals(1, result.orphanCount());
		assertEquals(1, result.zeroAvailableMatchedCount());
		assertEquals(0, result.zeroAvailableOrphanCount());
		assertEquals(receivedAt, existing.getReceivedAt()); assertEquals(30, existing.getTotalSpaces());
		verify(lots, times(1)).findByDataSourceAndExternalIdIn(
				org.mockito.ArgumentMatchers.eq(DataSource.PUBLIC_DATA_PORTAL), org.mockito.ArgumentMatchers.anyCollection());
		verify(realtime, times(1)).findByParkingLotIdIn(org.mockito.ArgumentMatchers.anyCollection());
		verify(realtime).saveAll(org.mockito.ArgumentMatchers.anyCollection());
	}
}
