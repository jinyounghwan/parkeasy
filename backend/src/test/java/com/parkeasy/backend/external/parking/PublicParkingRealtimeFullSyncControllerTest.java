package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicParkingRealtimeFullSyncControllerTest {
	@Test
	void exposesLimitedStartResumePauseAndJobSpecificStatus() throws Exception {
		PublicParkingRealtimeFullSyncService service = mock(PublicParkingRealtimeFullSyncService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicParkingRealtimeFullSyncController(service)).build();

		mvc.perform(post("/api/admin/parking-sync/realtime/full/start").param("maxPages", "3"))
				.andExpect(status().isAccepted());
		mvc.perform(post("/api/admin/parking-sync/realtime/full/resume")
				.param("jobId", "12").param("maxPages", "10"))
				.andExpect(status().isAccepted());
		mvc.perform(get("/api/admin/parking-sync/realtime/full/status").param("jobId", "12"))
				.andExpect(status().isOk());

		verify(service).start(3, 1, false, null);
		verify(service).resume(12L, 10, false, null);
		verify(service).status(12L);
	}
}
