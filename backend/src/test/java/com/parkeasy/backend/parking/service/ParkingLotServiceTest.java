package com.parkeasy.backend.parking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingType;
import com.parkeasy.backend.parking.domain.ParkingRealtime;
import com.parkeasy.backend.parking.domain.ParkingOperation;
import java.time.LocalTime;
import com.parkeasy.backend.parking.dto.ParkingLotDetailResponse;
import com.parkeasy.backend.parking.dto.ParkingLotSummaryResponse;
import com.parkeasy.backend.parking.dto.NearbyParkingLotResponse;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;
import com.parkeasy.backend.sync.RealtimeFreshnessPolicy;

class ParkingLotServiceTest {

	private ParkingLotRepository parkingLotRepository;
	private ParkingLotService parkingLotService;
	private ParkingOperationRepository parkingOperationRepository;
	private ParkingRealtimeRepository parkingRealtimeRepository;
	private RealtimeFreshnessPolicy freshnessPolicy;

	@BeforeEach
	void setUp() {
		parkingLotRepository = mock(ParkingLotRepository.class);
		parkingOperationRepository = mock(ParkingOperationRepository.class);
		parkingRealtimeRepository = mock(ParkingRealtimeRepository.class);
		freshnessPolicy = mock(RealtimeFreshnessPolicy.class);
		parkingLotService = new ParkingLotService(parkingLotRepository, parkingOperationRepository,
				parkingRealtimeRepository, freshnessPolicy);
	}

	@Test
	void findsAllParkingLotsAsSummaryResponses() {
		ParkingLot parkingLot = parkingLot(1L, "수원 테스트 주차장");
		when(parkingLotRepository.findAll()).thenReturn(List.of(parkingLot));

		List<ParkingLotSummaryResponse> result = parkingLotService.findAll();

		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).id());
		assertEquals("수원 테스트 주차장", result.get(0).name());
	}

	@Test
	void findsParkingLotByIdAsDetailResponse() {
		ParkingLot parkingLot = parkingLot(1L, "수원 테스트 주차장");
		when(parkingLotRepository.findById(1L)).thenReturn(Optional.of(parkingLot));

		ParkingLotDetailResponse result = parkingLotService.findById(1L);

		assertEquals(1L, result.id());
		assertEquals("수원 테스트 주차장", result.name());
	}

	@Test
	void throwsNotFoundExceptionWhenParkingLotDoesNotExist() {
		when(parkingLotRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(ParkingLotNotFoundException.class, () -> parkingLotService.findById(999L));
		verify(parkingLotRepository).findById(999L);
	}

	@Test
	void filtersByRadiusSortsByDistanceAndCalculatesDistanceMeters() {
		ParkingLot closest = parkingLot(
				1L, "closest", "37.2636000", "127.0286000");
		ParkingLot nearby = parkingLot(
				2L, "nearby", "37.2636000", "127.0296000");
		ParkingLot outsideRadius = parkingLot(
				3L, "outside", "37.2800000", "127.0286000");
		when(parkingLotRepository.findWithinBoundingBox(any(), any(), any(), any(),
				nullable(ParkingType.class), nullable(Boolean.class)))
				.thenReturn(List.of(outsideRadius, nearby, closest));

		List<NearbyParkingLotResponse> result = parkingLotService.findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 1000);

		assertEquals(2, result.size());
		assertEquals(1L, result.get(0).id());
		assertEquals(0L, result.get(0).distanceMeters());
		assertEquals(2L, result.get(1).id());
		assertTrue(result.get(1).distanceMeters() >= 88);
		assertTrue(result.get(1).distanceMeters() <= 90);
	}

	@Test
	void appliesLimitAfterDistanceSort() {
		ParkingLot farther = parkingLot(2L, "farther", "37.2636", "127.0296");
		ParkingLot closest = parkingLot(1L, "closest", "37.2636", "127.0286");
		stubCandidates(farther, closest);

		var result = parkingLotService.findNearby(new BigDecimal("37.2636"),
				new BigDecimal("127.0286"), 1000, NearbyParkingLotFilter.none(),
				NearbyParkingLotSort.DISTANCE, 1);

		assertEquals(List.of(1L), result.stream().map(NearbyParkingLotResponse::id).toList());
	}

	@Test
	void appliesLimitAfterFeeSort() {
		ParkingLot expensive = parkingLot(1L, "expensive"); when(expensive.getBaseFee()).thenReturn(1);
		ParkingLot cheap = parkingLot(2L, "cheap"); when(cheap.getBaseFee()).thenReturn(9999);
		ParkingLot free = parkingLot(3L, "free"); when(free.isFree()).thenReturn(true);
		stubCandidates(expensive, cheap, free);
		stubOperations(operation(expensive, 5000), operation(cheap, 1000), operation(free, 0));

		var result = parkingLotService.findNearby(new BigDecimal("37.2636"),
				new BigDecimal("127.0286"), 1000, NearbyParkingLotFilter.none(),
				NearbyParkingLotSort.FEE, 2);

		assertEquals(List.of(3L, 2L), result.stream().map(NearbyParkingLotResponse::id).toList());
	}

	@Test
	void appliesLimitAfterAvailableSortAndUsesOneRealtimeBatchQuery() {
		ParkingLot low = parkingLot(1L, "low"); ParkingLot high = parkingLot(2L, "high");
		stubCandidates(low, high);
		ParkingRealtime lowRealtime = new ParkingRealtime(low, 20, 2, null, LocalDateTime.now());
		ParkingRealtime highRealtime = new ParkingRealtime(high, 20, 15, null, LocalDateTime.now());
		when(parkingRealtimeRepository.findByParkingLotIdIn(any())).thenReturn(List.of(lowRealtime, highRealtime));

		var result = parkingLotService.findNearby(new BigDecimal("37.2636"),
				new BigDecimal("127.0286"), 1000, NearbyParkingLotFilter.none(),
				NearbyParkingLotSort.AVAILABLE, 1);

		assertEquals(List.of(2L), result.stream().map(NearbyParkingLotResponse::id).toList());
		verify(parkingRealtimeRepository, times(1)).findByParkingLotIdIn(any());
	}

	@Test
	void feeSortPlacesMissingFeesLast() {
		ParkingLot missing = parkingLot(1L, "missing"); when(missing.getBaseFee()).thenReturn(null);
		ParkingLot paid = parkingLot(2L, "paid"); when(paid.getBaseFee()).thenReturn(1000);
		ParkingLot free = parkingLot(3L, "free"); when(free.isFree()).thenReturn(true);
		stubCandidates(missing, paid, free);
        stubOperations(operation(missing, null), operation(paid, 1000), operation(free, 0));
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"),
				new BigDecimal("127.0286"), 1000, NearbyParkingLotFilter.none(), NearbyParkingLotSort.FEE, 100);
		assertEquals(List.of(3L, 2L, 1L), result.stream().map(NearbyParkingLotResponse::id).toList());
	}

	@Test
	void availableSortPreservesFreshZeroBeforeStaleAndMissingRealtime() {
		ParkingLot missing = parkingLot(1L, "missing"); ParkingLot stale = parkingLot(2L, "stale");
		ParkingLot zero = parkingLot(3L, "zero"); ParkingLot fresh = parkingLot(4L, "fresh");
		stubCandidates(missing, stale, zero, fresh);
		ParkingRealtime old = new ParkingRealtime(stale, 2_000_000, 1_500_000, null, LocalDateTime.now().minusDays(1));
		ParkingRealtime empty = new ParkingRealtime(zero, 100, 0, null, LocalDateTime.now());
		ParkingRealtime current = new ParkingRealtime(fresh, 100, 5, null, LocalDateTime.now());
		when(freshnessPolicy.isStale(old)).thenReturn(true);
		when(parkingRealtimeRepository.findByParkingLotIdIn(any())).thenReturn(List.of(old, empty, current));
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"),
				new BigDecimal("127.0286"), 1000, NearbyParkingLotFilter.none(), NearbyParkingLotSort.AVAILABLE, 100);
		assertEquals(List.of(4L, 3L, 2L, 1L), result.stream().map(NearbyParkingLotResponse::id).toList());
		assertEquals(0, result.get(1).availableSpaces());
		assertEquals(Boolean.FALSE, result.get(1).stale());
		assertEquals(Boolean.TRUE, result.get(2).stale());
		assertEquals(null, result.get(3).availableSpaces());
		assertEquals(null, result.get(3).stale());
	}

	private void stubCandidates(ParkingLot... lots) {
		when(parkingLotRepository.findWithinBoundingBox(any(), any(), any(), any(),
				nullable(ParkingType.class), nullable(Boolean.class)))
				.thenReturn(List.of(lots));
	}

	@Test
	void nearbyUsesOperationAndOneBatchQueryRatherThanLotSummary() {
		ParkingLot lot = parkingLot(141L, "canonical");
		when(lot.getBaseFee()).thenReturn(9999); when(lot.getBaseTime()).thenReturn(1);
		stubCandidates(lot); stubOperations(operation(lot, 3000));
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000);
		assertEquals(3000, result.get(0).baseFee()); assertEquals(30, result.get(0).baseTime());
		verify(parkingOperationRepository, times(1)).findByParkingLotIdIn(List.of(141L));
		verify(parkingOperationRepository, org.mockito.Mockito.never()).findByParkingLotId(any());
		verify(parkingRealtimeRepository, times(1)).findByParkingLotIdIn(List.of(141L));
		verify(parkingRealtimeRepository, org.mockito.Mockito.never()).findByParkingLotId(any());
	}

	@Test
	void missingOperationDoesNotFallBackToLegacyFlagsOrFees() {
		ParkingLot lot = parkingLot(1L, "missing"); when(lot.getBaseFee()).thenReturn(0);
		when(lot.isFree()).thenReturn(true); when(lot.isOpen24Hours()).thenReturn(true);
		stubCandidates(lot);
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000).get(0);
		assertEquals(null, result.baseTime()); assertEquals(null, result.baseFee());
		assertEquals(null, result.isFree()); assertEquals(null, result.is24Hours());
	}

	@Test
	void unsupportedFreeAnd24HourFiltersRejectBothTrueAndFalseBeforeQuerying() {
		for (Boolean flag : List.of(true, false)) {
			for (var filter : List.of(new NearbyParkingLotFilter(null, flag, null, null),
					new NearbyParkingLotFilter(null, null, flag, null))) {
				var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
						() -> parkingLotService.findNearby(new BigDecimal("37.2636"), new BigDecimal("127.0286"),
								2000, filter, NearbyParkingLotSort.FEE, 100));
				assertEquals(400, error.getStatusCode().value());
				assertTrue(error.getReason().contains("unsupported"));
			}
		}
		org.mockito.Mockito.verifyNoInteractions(parkingLotRepository, parkingOperationRepository, parkingRealtimeRepository);
	}

	@Test
	void zeroFeesAndTimeRangesDoNotInventClassifications() {
		ParkingLot midnight = parkingLot(1L, "midnight");
		ParkingLot fullDay = parkingLot(2L, "fullDay");
		ParkingOperation first = operation(midnight, 0), second = operation(fullDay, 0);
		setHours(first, LocalTime.MIDNIGHT); setHours(second, LocalTime.of(23, 59, 59));
		stubCandidates(midnight, fullDay); stubOperations(first, second);
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000);
		for (var item : result) {
			assertEquals(0, item.baseFee());
			assertEquals(null, item.isFree()); assertEquals(null, item.is24Hours());
		}
	}

	@Test
	void facilityCapacityIsIndependentOfRealtimeCapacity() {
		ParkingLot lot = parkingLot(141L, "differentTotals"); when(lot.getTotalSpaces()).thenReturn(17);
		stubCandidates(lot);
		when(parkingRealtimeRepository.findByParkingLotIdIn(any()))
				.thenReturn(List.of(new ParkingRealtime(lot, 2, 0, null, LocalDateTime.now())));
		var result = parkingLotService.findNearby(new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000).get(0);
		assertEquals(17, result.totalSpaces()); assertEquals(0, result.availableSpaces());
	}

	private void stubOperations(ParkingOperation... operations) {
		when(parkingOperationRepository.findByParkingLotIdIn(any())).thenReturn(List.of(operations));
	}

	private ParkingOperation operation(ParkingLot lot, Integer fee) {
		ParkingOperation operation = mock(ParkingOperation.class);
		when(operation.getParkingLot()).thenReturn(lot); when(operation.getBaseTime()).thenReturn(30);
		when(operation.getBaseFee()).thenReturn(fee); when(operation.getAdditionalFee()).thenReturn(0);
		when(operation.getDailyMaxFee()).thenReturn(0); when(operation.getMonthlyFee()).thenReturn(0);
		return operation;
	}

	private void setHours(ParkingOperation operation, LocalTime end) {
		when(operation.getMondayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getMondayCloseTime()).thenReturn(end);
		when(operation.getTuesdayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getTuesdayCloseTime()).thenReturn(end);
		when(operation.getWednesdayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getWednesdayCloseTime()).thenReturn(end);
		when(operation.getThursdayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getThursdayCloseTime()).thenReturn(end);
		when(operation.getFridayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getFridayCloseTime()).thenReturn(end);
		when(operation.getSaturdayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getSaturdayCloseTime()).thenReturn(end);
		when(operation.getSundayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getSundayCloseTime()).thenReturn(end);
		when(operation.getHolidayOpenTime()).thenReturn(LocalTime.MIDNIGHT); when(operation.getHolidayCloseTime()).thenReturn(end);
	}

	private ParkingLot parkingLot(Long id, String name) {
		return parkingLot(id, name, "37.2635737", "127.0286009");
	}

	private ParkingLot parkingLot(Long id, String name, String latitude, String longitude) {
		ParkingLot parkingLot = mock(ParkingLot.class);
		when(parkingLot.getId()).thenReturn(id);
		when(parkingLot.getName()).thenReturn(name);
		when(parkingLot.getLatitude()).thenReturn(new BigDecimal(latitude));
		when(parkingLot.getLongitude()).thenReturn(new BigDecimal(longitude));
		return parkingLot;
	}
}
