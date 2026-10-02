package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicParkingOperationFullSyncControllerTest {
	@Test
	void exposesBackgroundStartResumeAndJobSpecificStatus() throws Exception {
		PublicParkingOperationFullSyncService service = mock(PublicParkingOperationFullSyncService.class);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicParkingOperationFullSyncController(service)).build();

		mvc.perform(post("/api/admin/parking-sync/operations/full/start").param("maxPages", "3"))
				.andExpect(status().isAccepted());
		mvc.perform(post("/api/admin/parking-sync/operations/full/resume")
				.param("jobId", "9").param("maxPages", "10"))
				.andExpect(status().isAccepted());
		mvc.perform(get("/api/admin/parking-sync/operations/full/status").param("jobId", "9"))
				.andExpect(status().isOk());

		verify(service).start(3, 1, false, null);
		verify(service).resume(9L, 10, false, null);
		verify(service).status(9L);
	}
}
