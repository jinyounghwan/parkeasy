package com.parkeasy.backend.external.parking;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSyncJobRepository extends JpaRepository<ParkingSyncJob, Long> {
	Optional<ParkingSyncJob> findFirstBySyncTypeOrderByCreatedAtDesc(String syncType);
	Optional<ParkingSyncJob> findFirstBySyncTypeAndStatusInOrderByCreatedAtDesc(
			String syncType, Collection<ParkingSyncJobStatus> statuses);
}
