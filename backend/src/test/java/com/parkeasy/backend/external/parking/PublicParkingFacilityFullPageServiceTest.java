package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PublicParkingFacilityFullPageServiceTest {
	@Test
	void deduplicatesPageAndUpdatesCheckpointWithBatchCounts() {
		ParkingFacilityUpsertService upsertService = mock(ParkingFacilityUpsertService.class);
		ParkingSyncJobRepository jobRepository = mock(ParkingSyncJobRepository.class);
		ParkingSyncJob job = ParkingSyncJob.facilityFull(1, 100, 3);
		when(jobRepository.findById(1L)).thenReturn(java.util.Optional.of(job));
		when(upsertService.upsert(anyCollection())).thenReturn(new ParkingFacilityUpsertResult(1, 0));
		PublicParkingFacilityFullPageService service = new PublicParkingFacilityFullPageService(
				new ParkingFacilityMapper(), upsertService, jobRepository,
				mock(ParkingSyncSkippedPageRepository.class));

		ParkingFacilityFullPageResult result = service.process(1L, 1,
				response(item("A", "first", "37.1"), item("A", "last", "37.2"),
						item("B", "invalid", "bad")), 300, 3);

		assertEquals(3, result.processed());
		assertEquals(1, result.duplicates());
		assertEquals(1, result.skipped());
		assertEquals(2, job.getNextPage());
		assertEquals(1, job.getLastSuccessPage());
		ArgumentCaptor<java.util.Collection<ParkingFacilityData>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
		verify(upsertService).upsert(captor.capture());
		assertEquals(1, captor.getValue().size());
		assertEquals("last", captor.getValue().iterator().next().name());
	}

	@Test
	void recordsPermanentEmptyPageAndAdvancesCheckpointAtomically() {
		ParkingFacilityUpsertService upsertService = mock(ParkingFacilityUpsertService.class);
		ParkingSyncJobRepository jobRepository = mock(ParkingSyncJobRepository.class);
		ParkingSyncSkippedPageRepository skippedRepository = mock(ParkingSyncSkippedPageRepository.class);
		ParkingSyncJob job = ParkingSyncJob.facilityFull(1, 1000, null);
		ReflectionTestUtils.setField(job, "id", 9L);
		when(jobRepository.findById(9L)).thenReturn(java.util.Optional.of(job));
		PublicParkingFacilityFullPageService service = new PublicParkingFacilityFullPageService(
				new ParkingFacilityMapper(), upsertService, jobRepository, skippedRepository);

		service.skipEmptyPage(9L, 1511, 6, 1_767_934, 1768);

		assertEquals(1511, job.getLastSuccessPage());
		assertEquals(1512, job.getNextPage());
		assertEquals(1, job.getSkippedPageCount());
		ArgumentCaptor<ParkingSyncSkippedPage> captor = ArgumentCaptor.forClass(ParkingSyncSkippedPage.class);
		verify(skippedRepository).save(captor.capture());
		assertEquals(ParkingSyncSkipReason.EMPTY_SUCCESS_PAGE, captor.getValue().getSkipReason());
		assertEquals(6, captor.getValue().getAttemptCount());
	}

	@Test
	void bootstrapsTrailingRunFromExistingSkippedPageRecords() {
		ParkingSyncJobRepository jobRepository = mock(ParkingSyncJobRepository.class);
		ParkingSyncSkippedPageRepository skippedRepository = mock(ParkingSyncSkippedPageRepository.class);
		ParkingSyncJob job = ParkingSyncJob.facilityFull(1, 1000, null);
		ReflectionTestUtils.setField(job, "id", 9L);
		ReflectionTestUtils.setField(job, "lastSuccessPage", 1597);
		ReflectionTestUtils.setField(job, "nextPage", 1598);
		when(jobRepository.findById(9L)).thenReturn(java.util.Optional.of(job));
		List<ParkingSyncSkippedPage> existing = IntStream.iterate(1597, page -> page - 1)
				.limit(10)
				.mapToObj(page -> ParkingSyncSkippedPage.emptySuccess(9L, page, 3, 1_767_934))
				.toList();
		when(skippedRepository.findByJobIdAndPageNoLessThanEqualOrderByPageNoDesc(9L, 1597))
				.thenReturn(existing);
		PublicParkingFacilityFullPageService service = new PublicParkingFacilityFullPageService(
				new ParkingFacilityMapper(), mock(ParkingFacilityUpsertService.class),
				jobRepository, skippedRepository);

		service.bootstrapTrailingEmptyPages(9L, 10);

		assertEquals(10, job.getConsecutiveEmptyPageCount());
		assertEquals(1588, job.getFirstTrailingEmptyPage());
		assertEquals(1587, job.getLastDataPage());
	}

	private PublicParkingFacilityResponse response(PublicParkingFacilityItem... items) {
		return new PublicParkingFacilityResponse(List.of(items), "1", "0", "300", "100", "SUCCESS");
	}

	private PublicParkingFacilityItem item(String id, String name, String latitude) {
		return new PublicParkingFacilityItem(id, name, "address", "sido", "sigungu",
				latitude, "127.1", "10");
	}
}
