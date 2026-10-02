package com.parkeasy.backend.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.parkeasy.backend.external.parking.ParkingFacilitySyncResult;
import com.parkeasy.backend.external.parking.ParkingOperationSyncResult;
import com.parkeasy.backend.external.parking.ParkingRealtimeSyncResult;
import com.parkeasy.backend.external.parking.PublicParkingFacilitySyncService;
import com.parkeasy.backend.external.parking.PublicParkingOperationSyncService;
import com.parkeasy.backend.external.parking.PublicParkingRealtimeSyncService;

@Component
public class ParkingSyncScheduler {
	private static final Logger log = LoggerFactory.getLogger(ParkingSyncScheduler.class);
	private final PublicParkingFacilitySyncService facilitySyncService;
	private final PublicParkingOperationSyncService operationSyncService;
	private final PublicParkingRealtimeSyncService realtimeSyncService;
	private final ParkingSyncProperties properties;
	private final AtomicBoolean facilityRunning = new AtomicBoolean();
	private final AtomicBoolean operationRunning = new AtomicBoolean();
	private final AtomicBoolean realtimeRunning = new AtomicBoolean();

	public ParkingSyncScheduler(PublicParkingFacilitySyncService facilitySyncService,
			PublicParkingOperationSyncService operationSyncService,
			PublicParkingRealtimeSyncService realtimeSyncService, ParkingSyncProperties properties) {
		this.facilitySyncService = facilitySyncService; this.operationSyncService = operationSyncService;
		this.realtimeSyncService = realtimeSyncService; this.properties = properties;
	}

	@Scheduled(cron = "${parkeasy.sync.facility.cron:0 0 3 * * *}")
	public void syncFacilities() {
		if (!properties.facility().enabled()) return;
		runLocked("facility", facilityRunning, () -> facilityData(facilitySyncService.sync(
				properties.facility().sido(), properties.facility().sigungu(), properties.facility().maxPages())));
	}

	@Scheduled(cron = "${parkeasy.sync.operation.cron:0 30 3 * * *}")
	public void syncOperations() {
		if (!properties.operation().enabled()) return;
		runLocked("operation", operationRunning,
				() -> operationData(operationSyncService.sync(properties.operation().maxPages())));
	}

	@Scheduled(fixedDelayString = "${parkeasy.sync.realtime.fixed-delay:600000}",
			initialDelayString = "${parkeasy.sync.realtime.initial-delay:600000}")
	public void syncRealtime() {
		if (!properties.realtime().enabled()) return;
		runLocked("realtime", realtimeRunning,
				() -> realtimeData(realtimeSyncService.sync(properties.realtime().maxPages())));
	}

	private void runLocked(String type, AtomicBoolean lock, Supplier<SyncLogData> sync) {
		if (!lock.compareAndSet(false, true)) { log.info("Parking sync skipped: type={}, reason=already-running", type); return; }
		Instant startedAt = Instant.now();
		try {
			SyncLogData result = sync.get();
			Instant endedAt = Instant.now();
			log.info("Parking sync completed: type={}, startedAt={}, endedAt={}, durationMs={}, fetched={}, matched={}, inserted={}, updated={}, skipped={}, duplicates={}",
					type, startedAt, endedAt, Duration.between(startedAt, endedAt).toMillis(), result.fetched(),
					result.matched(), result.inserted(), result.updated(), result.skipped(), result.duplicates());
		}
		catch (RuntimeException exception) {
			log.error("Parking sync failed: type={}, durationMs={}, exceptionType={}, message=external-sync-failed",
					type, Duration.between(startedAt, Instant.now()).toMillis(), exception.getClass().getSimpleName());
		} finally { lock.set(false); }
	}

	private SyncLogData facilityData(ParkingFacilitySyncResult result) {
		return new SyncLogData(result.fetchedCount(), result.matchedCount(), result.insertedCount(),
				result.updatedCount(), result.skippedCount(), result.duplicateCount());
	}
	private SyncLogData operationData(ParkingOperationSyncResult result) {
		return new SyncLogData(result.fetchedCount(), result.matchedParkingLotCount(), result.insertedCount(),
				result.updatedCount(), result.skippedCount(), result.duplicateCount());
	}
	private SyncLogData realtimeData(ParkingRealtimeSyncResult result) {
		return new SyncLogData(result.fetchedCount(), result.matchedParkingLotCount(), result.insertedCount(),
				result.updatedCount(), result.skippedCount(), result.duplicateCount());
	}

	private record SyncLogData(int fetched, int matched, int inserted, int updated, int skipped, int duplicates) { }
}
