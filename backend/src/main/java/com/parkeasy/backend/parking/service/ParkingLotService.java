package com.parkeasy.backend.parking.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.parkeasy.backend.parking.dto.ParkingLotDetailResponse;
import com.parkeasy.backend.parking.dto.ParkingLotSummaryResponse;
import com.parkeasy.backend.parking.dto.NearbyParkingLotResponse;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;
import com.parkeasy.backend.parking.dto.ParkingOperationResponse;
import com.parkeasy.backend.parking.dto.ParkingRealtimeResponse;
import com.parkeasy.backend.parking.domain.ParkingRealtime;
import com.parkeasy.backend.sync.RealtimeFreshnessPolicy;

@Service
@Transactional(readOnly = true)
public class ParkingLotService {
	private static final double EARTH_RADIUS_METERS = 6_371_000.0;

	private final ParkingLotRepository parkingLotRepository;
	private final ParkingOperationRepository parkingOperationRepository;
	private final ParkingRealtimeRepository parkingRealtimeRepository;
	private final RealtimeFreshnessPolicy freshnessPolicy;

	public ParkingLotService(ParkingLotRepository parkingLotRepository,
			ParkingOperationRepository parkingOperationRepository,
			ParkingRealtimeRepository parkingRealtimeRepository,
			RealtimeFreshnessPolicy freshnessPolicy) {
		this.parkingLotRepository = parkingLotRepository;
		this.parkingOperationRepository = parkingOperationRepository;
		this.parkingRealtimeRepository = parkingRealtimeRepository;
		this.freshnessPolicy = freshnessPolicy;
	}

	public List<ParkingLotSummaryResponse> findAll() {
		return parkingLotRepository.findAll().stream()
				.map(ParkingLotSummaryResponse::from)
				.toList();
	}

	public ParkingLotDetailResponse findById(Long id) {
		return parkingLotRepository.findById(id)
				.map(parkingLot -> ParkingLotDetailResponse.from(parkingLot,
						parkingOperationRepository.findByParkingLotId(id)
								.map(ParkingOperationResponse::from).orElse(null),
						parkingRealtimeRepository.findByParkingLotId(id)
								.map(realtime -> ParkingRealtimeResponse.from(
										realtime, freshnessPolicy.isStale(realtime))).orElse(null)))
				.orElseThrow(() -> new ParkingLotNotFoundException(id));
	}

	public List<NearbyParkingLotResponse> findNearby(
			BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
		return findNearby(latitude, longitude, radiusMeters, NearbyParkingLotFilter.none(),
				NearbyParkingLotSort.DISTANCE, 100);
	}

	public List<NearbyParkingLotResponse> findNearby(
			BigDecimal latitude, BigDecimal longitude, int radiusMeters,
			NearbyParkingLotFilter filter) {
		return findNearby(latitude, longitude, radiusMeters, filter,
				NearbyParkingLotSort.DISTANCE, 100);
	}

	public List<NearbyParkingLotResponse> findNearby(
			BigDecimal latitude, BigDecimal longitude, int radiusMeters,
			NearbyParkingLotFilter filter, NearbyParkingLotSort sort, int limit) {
		if (filter.free() != null || filter.open24Hours() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"free/open24Hours filters are unsupported: provider classifications unavailable");
		}
		double requestLatitude = latitude.doubleValue();
		double requestLongitude = longitude.doubleValue();
		BoundingBox boundingBox = calculateBoundingBox(
				requestLatitude, requestLongitude, radiusMeters);

		List<ParkingLot> candidates = boundingBox.crossesDateLine()
				? parkingLotRepository.findWithinDateLineCrossingBoundingBox(
						boundingBox.minLatitude(), boundingBox.maxLatitude(),
						boundingBox.minLongitude(), boundingBox.maxLongitude(),
						filter.parkingType(), filter.realtimeAvailable())
				: parkingLotRepository.findWithinBoundingBox(
						boundingBox.minLatitude(), boundingBox.maxLatitude(),
						boundingBox.minLongitude(), boundingBox.maxLongitude(),
						filter.parkingType(), filter.realtimeAvailable());

		List<ParkingLotDistance> withinRadius = candidates.stream()
				.map(parkingLot -> new ParkingLotDistance(
						parkingLot,
						calculateDistanceMeters(
								requestLatitude,
								requestLongitude,
								parkingLot.getLatitude().doubleValue(),
								parkingLot.getLongitude().doubleValue())))
				.filter(result -> result.distanceMeters() <= radiusMeters)
				.toList();
		if (withinRadius.isEmpty()) return List.of();
		List<Long> lotIds = withinRadius.stream().map(result -> result.parkingLot().getId()).toList();
		Map<Long, ParkingOperationResponse> operationByLotId = parkingOperationRepository.findByParkingLotIdIn(lotIds)
				.stream().collect(Collectors.toMap(operation -> operation.getParkingLot().getId(), ParkingOperationResponse::from));
		Map<Long, ParkingRealtime> realtimeByLotId = parkingRealtimeRepository
				.findByParkingLotIdIn(lotIds)
				.stream().collect(Collectors.toMap(realtime -> realtime.getParkingLot().getId(), Function.identity()));
		return withinRadius.stream()
				.map(result -> NearbyParkingLotResponse.from(
						result.parkingLot(), result.distanceMeters(),
						realtimeByLotId.containsKey(result.parkingLot().getId())
								? realtimeByLotId.get(result.parkingLot().getId()).getAvailableSpaces() : null,
						realtimeByLotId.containsKey(result.parkingLot().getId())
								? freshnessPolicy.isStale(realtimeByLotId.get(result.parkingLot().getId())) : null,
						operationByLotId.get(result.parkingLot().getId())))
				.sorted(responseComparator(sort))
				.limit(limit)
				.toList();
	}

	private Comparator<NearbyParkingLotResponse> responseComparator(NearbyParkingLotSort sort) {
		Comparator<NearbyParkingLotResponse> distance = Comparator.comparingLong(
				NearbyParkingLotResponse::distanceMeters);
		return switch (sort) {
			case DISTANCE -> distance;
			case FEE -> Comparator.comparingLong(this::feeSortValue).thenComparing(distance);
			case AVAILABLE -> Comparator.comparingInt(this::availabilityGroup)
					.thenComparing(Comparator.comparingLong(this::availableSortValue).reversed())
					.thenComparing(distance);
		};
	}

	private long feeSortValue(NearbyParkingLotResponse response) {
		return response.baseFee() == null ? Long.MAX_VALUE : response.baseFee();
	}

	private long availableSortValue(NearbyParkingLotResponse response) {
		return response.availableSpaces() == null ? Long.MIN_VALUE : response.availableSpaces();
	}

	private int availabilityGroup(NearbyParkingLotResponse response) {
		if (response.availableSpaces() == null) return 2;
		return Boolean.FALSE.equals(response.stale()) ? 0 : 1;
	}

	private BoundingBox calculateBoundingBox(double latitude, double longitude, int radiusMeters) {
		double angularDistance = radiusMeters / EARTH_RADIUS_METERS;
		double latitudeDelta = Math.toDegrees(angularDistance);
		double minLatitude = Math.max(-90.0, latitude - latitudeDelta);
		double maxLatitude = Math.min(90.0, latitude + latitudeDelta);

		double longitudeDelta;
		if (minLatitude <= -90.0 || maxLatitude >= 90.0) {
			longitudeDelta = 180.0;
		} else {
			double ratio = Math.sin(angularDistance) / Math.cos(Math.toRadians(latitude));
			longitudeDelta = Math.toDegrees(Math.asin(Math.min(1.0, ratio)));
		}

		double rawMinLongitude = longitude - longitudeDelta;
		double rawMaxLongitude = longitude + longitudeDelta;
		boolean crossesDateLine = rawMinLongitude < -180.0 || rawMaxLongitude > 180.0;
		double minLongitude = normalizeLongitude(rawMinLongitude);
		double maxLongitude = normalizeLongitude(rawMaxLongitude);
		if (longitudeDelta == 180.0) {
			minLongitude = -180.0;
			maxLongitude = 180.0;
			crossesDateLine = false;
		}

		return new BoundingBox(
				BigDecimal.valueOf(minLatitude),
				BigDecimal.valueOf(maxLatitude),
				BigDecimal.valueOf(minLongitude),
				BigDecimal.valueOf(maxLongitude),
				crossesDateLine);
	}

	private double normalizeLongitude(double longitude) {
		return ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
	}

	private double calculateDistanceMeters(
			double latitude1, double longitude1, double latitude2, double longitude2) {
		double latitudeDelta = Math.toRadians(latitude2 - latitude1);
		double longitudeDelta = Math.toRadians(longitude2 - longitude1);
		double latitude1Radians = Math.toRadians(latitude1);
		double latitude2Radians = Math.toRadians(latitude2);

		double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
				+ Math.cos(latitude1Radians) * Math.cos(latitude2Radians)
				* Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
		double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
		return EARTH_RADIUS_METERS * centralAngle;
	}

	private record BoundingBox(
			BigDecimal minLatitude,
			BigDecimal maxLatitude,
			BigDecimal minLongitude,
			BigDecimal maxLongitude,
			boolean crossesDateLine) {
	}

	private record ParkingLotDistance(ParkingLot parkingLot, double distanceMeters) {
	}
}
