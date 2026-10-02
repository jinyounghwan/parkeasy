package com.parkeasy.backend.parking.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parkeasy.backend.parking.domain.ParkingRealtime;

public interface ParkingRealtimeRepository extends JpaRepository<ParkingRealtime, Long> {
	Optional<ParkingRealtime> findByParkingLotId(Long parkingLotId);
	List<ParkingRealtime> findByParkingLotIdIn(Collection<Long> parkingLotIds);
	long countByParkingLotId(Long parkingLotId);
}
