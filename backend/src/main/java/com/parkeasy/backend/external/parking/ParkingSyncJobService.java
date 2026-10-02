package com.parkeasy.backend.external.parking;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkingSyncJobService {
	private final ParkingSyncJobRepository repository;

	public ParkingSyncJobService(ParkingSyncJobRepository repository) { this.repository = repository; }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ParkingSyncJob create(int startPage, int numOfRows, Integer pageLimit) {
		return repository.save(ParkingSyncJob.facilityFull(startPage, numOfRows, pageLimit));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ParkingSyncJob createOperation(int startPage, int numOfRows, Integer pageLimit) {
		return repository.save(ParkingSyncJob.operationFull(startPage, numOfRows, pageLimit));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ParkingSyncJob createRealtime(int startPage, int numOfRows, Integer pageLimit) {
		return repository.save(ParkingSyncJob.realtimeFull(startPage, numOfRows, pageLimit));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRunning(long jobId) { required(jobId).markRunning(); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void prepareExplicitResume(long jobId, Integer additionalPageLimit) {
		required(jobId).prepareExplicitResume(additionalPageLimit);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPageStarted(long jobId, int pageNo) { required(jobId).markPageStarted(pageNo); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordPageFailure(long jobId, int pageNo, PublicDataFailure failure) {
		required(jobId).recordPageFailure(pageNo, failure);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordEmptySuccessFailure(long jobId, int pageNo, PublicDataFailure failure) {
		required(jobId).recordEmptySuccessFailure(pageNo, failure);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordPartialPageFailure(long jobId, int pageNo, PublicDataFailure failure) {
		required(jobId).recordPartialPageFailure(pageNo, failure);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markAutoResume(long jobId) { required(jobId).markAutoResume(); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPaused(long jobId) { required(jobId).markPaused(); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(long jobId) { required(jobId).markCompleted(); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompletedForTrailingEmptyPages(long jobId) {
		required(jobId).markCompletedForTrailingEmptyPages();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void repairPrematureCompletion(long jobId) { required(jobId).repairPrematureCompletion(); }

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(long jobId, int pageNo, PublicDataFailure failure) {
		required(jobId).markFailed(pageNo, failure, "Parking full sync page failed");
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob required(long jobId) {
		return repository.findById(jobId).orElseThrow(() -> new IllegalStateException("Sync job not found"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latest() {
		return repository.findFirstBySyncTypeOrderByCreatedAtDesc(ParkingSyncJob.FACILITY_FULL)
				.orElseThrow(() -> new IllegalStateException("Facility full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latestOperation() {
		return repository.findFirstBySyncTypeOrderByCreatedAtDesc(ParkingSyncJob.OPERATION_FULL)
				.orElseThrow(() -> new IllegalStateException("Operation full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latestRealtime() {
		return repository.findFirstBySyncTypeOrderByCreatedAtDesc(ParkingSyncJob.REALTIME_FULL)
				.orElseThrow(() -> new IllegalStateException("Realtime full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latestResumable() {
		return repository.findFirstBySyncTypeAndStatusInOrderByCreatedAtDesc(ParkingSyncJob.FACILITY_FULL,
				List.of(ParkingSyncJobStatus.FAILED, ParkingSyncJobStatus.PAUSED,
						ParkingSyncJobStatus.RUNNING, ParkingSyncJobStatus.READY))
				.orElseThrow(() -> new IllegalStateException("Resumable facility full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob requiredResumable(long jobId) {
		ParkingSyncJob job = required(jobId);
		if (!List.of(ParkingSyncJobStatus.FAILED, ParkingSyncJobStatus.PAUSED,
				ParkingSyncJobStatus.RUNNING, ParkingSyncJobStatus.READY).contains(job.getStatus())) {
			throw new IllegalArgumentException("Sync job is not resumable");
		}
		return job;
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latestOperationResumable() {
		return repository.findFirstBySyncTypeAndStatusInOrderByCreatedAtDesc(ParkingSyncJob.OPERATION_FULL,
				List.of(ParkingSyncJobStatus.FAILED, ParkingSyncJobStatus.PAUSED,
						ParkingSyncJobStatus.RUNNING, ParkingSyncJobStatus.READY))
				.orElseThrow(() -> new IllegalStateException("Resumable operation full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob requiredOperationResumable(long jobId) {
		ParkingSyncJob job = requiredResumable(jobId);
		if (!ParkingSyncJob.OPERATION_FULL.equals(job.getSyncType())) {
			throw new IllegalArgumentException("Sync job is not an operation full sync job");
		}
		return job;
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob latestRealtimeResumable() {
		return repository.findFirstBySyncTypeAndStatusInOrderByCreatedAtDesc(ParkingSyncJob.REALTIME_FULL,
				List.of(ParkingSyncJobStatus.FAILED, ParkingSyncJobStatus.PAUSED,
						ParkingSyncJobStatus.RUNNING, ParkingSyncJobStatus.READY))
				.orElseThrow(() -> new IllegalStateException("Resumable realtime full sync job does not exist"));
	}

	@Transactional(readOnly = true)
	public ParkingSyncJob requiredRealtimeResumable(long jobId) {
		ParkingSyncJob job = requiredResumable(jobId);
		if (!ParkingSyncJob.REALTIME_FULL.equals(job.getSyncType())) {
			throw new IllegalArgumentException("Sync job is not a realtime full sync job");
		}
		return job;
	}

}
