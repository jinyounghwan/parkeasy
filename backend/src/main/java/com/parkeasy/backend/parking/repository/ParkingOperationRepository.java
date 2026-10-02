package com.parkeasy.backend.parking.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parkeasy.backend.parking.domain.ParkingOperation;

public interface ParkingOperationRepository extends JpaRepository<ParkingOperation, Long> {
	Optional<ParkingOperation> findByParkingLotId(Long parkingLotId);
	long countByParkingLotId(Long parkingLotId);
	List<ParkingOperation> findByParkingLotIdIn(Collection<Long> parkingLotIds);
}
