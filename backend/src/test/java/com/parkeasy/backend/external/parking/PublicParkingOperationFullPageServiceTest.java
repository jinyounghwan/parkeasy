package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublicParkingOperationFullPageServiceTest {
	@Test
	void keepsLastValidDuplicateAndSeparatesInvalidMissingAndOrphanCounts() {
		ParkingOperationUpsertService upsert = mock(ParkingOperationUpsertService.class);
		ParkingSyncJobRepository jobs = mock(ParkingSyncJobRepository.class);
		ParkingSyncJob job = ParkingSyncJob.operationFull(1, 1000, 1);
		when(jobs.findById(1L)).thenReturn(Optional.of(job));
		when(upsert.upsertBatch(anyCollection())).thenReturn(new ParkingOperationFullUpsertResult(1, 1, 0, 1));
		PublicParkingOperationFullPageService service = new PublicParkingOperationFullPageService(
				new ParkingOperationParser(), upsert, jobs, mock(ParkingSyncSkippedPageRepository.class));

		ParkingOperationFullPageResult result = service.process(1L, 1,
				response(item("same", "10"), item("same", null), item("same", "20"),
						item("orphan", "30"), item(null, "40")), 5, 1);

		assertEquals(5, result.processed()); assertEquals(2, result.duplicates());
		assertEquals(1, result.invalidOperationCount()); assertEquals(1, result.missingExternalIdCount());
		assertEquals(1, result.orphanCount()); assertEquals(1, job.getNextPage() - 1);
		ArgumentCaptor<java.util.Collection<ParkingOperationCandidate>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(upsert).upsertBatch(captor.capture());
		ParkingOperationCandidate same = captor.getValue().stream().filter(value -> value.externalId().equals("same")).findFirst().orElseThrow();
		assertEquals(20, same.details().baseTime());
	}

	private PublicParkingOperationResponse response(PublicParkingOperationItem... items) {
		return new PublicParkingOperationResponse("1", List.of(items), "0", "5", "1000", "SUCCESS");
	}
	private PublicParkingOperationItem item(String id, String baseTime) {
		return new PublicParkingOperationItem(id, null, null, null, null, null, null, null, null, null,
				baseTime == null ? null : new PublicParkingBasicInfo(baseTime, null, null, null), null);
	}
}
