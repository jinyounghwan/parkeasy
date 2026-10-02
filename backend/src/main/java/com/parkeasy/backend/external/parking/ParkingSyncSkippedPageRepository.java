package com.parkeasy.backend.external.parking;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ParkingSyncSkippedPageRepository extends JpaRepository<ParkingSyncSkippedPage, Long> {
	List<ParkingSyncSkippedPage> findTop10ByJobIdOrderByCreatedAtDesc(long jobId);
	List<ParkingSyncSkippedPage> findByJobIdAndPageNoLessThanEqualOrderByPageNoDesc(
			long jobId, int pageNo);
	@Transactional
	void deleteByJobId(long jobId);
}
