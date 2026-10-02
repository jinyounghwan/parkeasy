package com.parkeasy.backend.sync;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.parkeasy.backend.external.parking.ParkingFacilitySyncResult;
import com.parkeasy.backend.external.parking.ParkingOperationSyncResult;
import com.parkeasy.backend.external.parking.ParkingRealtimeSyncResult;
import com.parkeasy.backend.external.parking.PublicParkingFacilitySyncService;
import com.parkeasy.backend.external.parking.PublicParkingOperationSyncService;
import com.parkeasy.backend.external.parking.PublicParkingRealtimeSyncService;

class ParkingSyncSchedulerTest {
	@Test
	void disabledJobsDoNotInvokeServices() {
		Fixture fixture = fixture(false);
		fixture.scheduler.syncFacilities(); fixture.scheduler.syncOperations(); fixture.scheduler.syncRealtime();
		verify(fixture.facility, never()).sync("서울특별시", "강북구", 1);
		verify(fixture.operation, never()).sync(1); verify(fixture.realtime, never()).sync(1);
	}

	@Test
	void enabledJobsInvokeOrchestrationServices() {
		Fixture fixture = fixture(true);
		fixture.scheduler.syncFacilities(); fixture.scheduler.syncOperations(); fixture.scheduler.syncRealtime();
		verify(fixture.facility).sync("서울특별시", "강북구", 1);
		verify(fixture.operation).sync(1); verify(fixture.realtime).sync(1);
	}

	@Test
	void skipsSameJobWhileItIsAlreadyRunning() throws Exception {
		Fixture fixture = fixture(true);
		CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
		when(fixture.realtime.sync(1)).thenAnswer(invocation -> {
			entered.countDown(); release.await(3, TimeUnit.SECONDS); return realtimeResult();
		});
		CompletableFuture<Void> first = CompletableFuture.runAsync(fixture.scheduler::syncRealtime);
		entered.await(3, TimeUnit.SECONDS);
		fixture.scheduler.syncRealtime();
		release.countDown(); first.get(3, TimeUnit.SECONDS);
		verify(fixture.realtime, times(1)).sync(1);
	}

	@Test
	void exceptionReleasesLockAndDoesNotBlockNextRun() {
		Fixture fixture = fixture(true);
		when(fixture.realtime.sync(1)).thenThrow(new IllegalStateException("safe-test-failure"))
				.thenReturn(realtimeResult());
		fixture.scheduler.syncRealtime(); fixture.scheduler.syncRealtime();
		verify(fixture.realtime, times(2)).sync(1);
	}

	private Fixture fixture(boolean enabled) {
		PublicParkingFacilitySyncService facility = mock(PublicParkingFacilitySyncService.class);
		PublicParkingOperationSyncService operation = mock(PublicParkingOperationSyncService.class);
		PublicParkingRealtimeSyncService realtime = mock(PublicParkingRealtimeSyncService.class);
		when(facility.sync("서울특별시", "강북구", 1)).thenReturn(
				new ParkingFacilitySyncResult(1, 10, 2, 2, 0, 8, 0));
		when(operation.sync(1)).thenReturn(new ParkingOperationSyncResult(1, 10, 2, 2, 0, 8, 0));
		when(realtime.sync(1)).thenReturn(realtimeResult());
		ParkingSyncProperties properties = new ParkingSyncProperties(
				new ParkingSyncProperties.Facility(enabled, "0 0 3 * * *", 1, "서울특별시", "강북구"),
				new ParkingSyncProperties.Operation(enabled, "0 30 3 * * *", 1),
				new ParkingSyncProperties.Realtime(enabled, 600000, 600000, 1, 30));
		return new Fixture(facility, operation, realtime,
				new ParkingSyncScheduler(facility, operation, realtime, properties));
	}

	private ParkingRealtimeSyncResult realtimeResult() {
		return new ParkingRealtimeSyncResult(1, 10, 2, 2, 0, 8, 0);
	}

	private record Fixture(PublicParkingFacilitySyncService facility,
			PublicParkingOperationSyncService operation, PublicParkingRealtimeSyncService realtime,
			ParkingSyncScheduler scheduler) { }
}
