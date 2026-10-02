package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicParkingFacilityFullSyncControllerTest {
	@Test
	void startAndResumeReturnAcceptedImmediately() throws Exception {
		PublicParkingFacilityFullSyncService service = mock(PublicParkingFacilityFullSyncService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new PublicParkingFacilityFullSyncController(service)).build();

		mvc.perform(post("/api/admin/parking-sync/facilities/full/start").param("maxPages", "10"))
				.andExpect(status().isAccepted());
		mvc.perform(post("/api/admin/parking-sync/facilities/full/resume").param("maxPages", "10"))
				.andExpect(status().isAccepted());
		verify(service).start(10, 1, false, null);
		verify(service).resume(null, 10, false, null);
	}

	@Test
	void statusSupportsSpecificJobId() throws Exception {
		PublicParkingFacilityFullSyncService service = mock(PublicParkingFacilityFullSyncService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(
				new PublicParkingFacilityFullSyncController(service)).build();

		mvc.perform(get("/api/admin/parking-sync/facilities/full/status").param("jobId", "9"))
				.andExpect(status().isOk());
		verify(service).status(9L);
	}
}
