package com.parkeasy.backend.parking.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingType;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

	Optional<ParkingLot> findByDataSourceAndExternalId(DataSource dataSource, String externalId);

	List<ParkingLot> findByDataSourceAndExternalIdIn(DataSource dataSource, Collection<String> externalIds);

	default List<ParkingLot> findWithinBoundingBox(
			BigDecimal minLatitude, BigDecimal maxLatitude,
			BigDecimal minLongitude, BigDecimal maxLongitude) {
		return findWithinBoundingBox(minLatitude, maxLatitude, minLongitude, maxLongitude,
				null, null);
	}

	@Query("""
			select parkingLot
			from ParkingLot parkingLot
			where parkingLot.latitude between :minLatitude and :maxLatitude
			  and parkingLot.longitude between :minLongitude and :maxLongitude
			  and parkingLot.latitude <> 0
			  and parkingLot.longitude <> 0
			  and (parkingLot.externalId is null or parkingLot.externalId not like 'SAMPLE-%')
			  and (:parkingType is null or parkingLot.parkingType = :parkingType)
			  and (:realtimeAvailable is null
			       or (:realtimeAvailable = true and exists (select realtime.id from ParkingRealtime realtime where realtime.parkingLot = parkingLot))
			       or (:realtimeAvailable = false and not exists (select realtime.id from ParkingRealtime realtime where realtime.parkingLot = parkingLot)))
			""")
	List<ParkingLot> findWithinBoundingBox(
			@Param("minLatitude") BigDecimal minLatitude,
			@Param("maxLatitude") BigDecimal maxLatitude,
			@Param("minLongitude") BigDecimal minLongitude,
			@Param("maxLongitude") BigDecimal maxLongitude,
			@Param("parkingType") ParkingType parkingType,
			@Param("realtimeAvailable") Boolean realtimeAvailable);

	@Query("""
			select parkingLot
			from ParkingLot parkingLot
			where parkingLot.latitude between :minLatitude and :maxLatitude
			  and (parkingLot.longitude >= :minLongitude
			       or parkingLot.longitude <= :maxLongitude)
			  and parkingLot.latitude <> 0
			  and parkingLot.longitude <> 0
			  and (parkingLot.externalId is null or parkingLot.externalId not like 'SAMPLE-%')
			  and (:parkingType is null or parkingLot.parkingType = :parkingType)
			  and (:realtimeAvailable is null
			       or (:realtimeAvailable = true and exists (select realtime.id from ParkingRealtime realtime where realtime.parkingLot = parkingLot))
			       or (:realtimeAvailable = false and not exists (select realtime.id from ParkingRealtime realtime where realtime.parkingLot = parkingLot)))
			""")
	List<ParkingLot> findWithinDateLineCrossingBoundingBox(
			@Param("minLatitude") BigDecimal minLatitude,
			@Param("maxLatitude") BigDecimal maxLatitude,
			@Param("minLongitude") BigDecimal minLongitude,
			@Param("maxLongitude") BigDecimal maxLongitude,
			@Param("parkingType") ParkingType parkingType,
			@Param("realtimeAvailable") Boolean realtimeAvailable);

	default List<ParkingLot> findWithinDateLineCrossingBoundingBox(
			BigDecimal minLatitude, BigDecimal maxLatitude,
			BigDecimal minLongitude, BigDecimal maxLongitude) {
		return findWithinDateLineCrossingBoundingBox(minLatitude, maxLatitude,
				minLongitude, maxLongitude, null, null);
	}
}
