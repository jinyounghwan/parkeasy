package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class PublicDataExceptionSafetyTest {
	@Test
	void errorsDoNotReturnUpstreamBodyUriOrCause() throws Exception {
		var service = mock(PublicParkingOperationSyncService.class);
		var mvc = MockMvcBuilders.standaloneSetup(new ParkingOperationSyncController(service))
				.setControllerAdvice(new PublicDataApiExceptionHandler()).build();
		RuntimeException[] failures = {new ResourceAccessException("fixture-sensitive-marker"),
				new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "fixture-sensitive-marker"),
				new IllegalStateException("fixture-sensitive-marker", new RuntimeException("fixture-sensitive-marker")),
				new IllegalArgumentException("fixture-sensitive-marker")};
		int[] statuses = {504, 502, 500, 400};
		for (int i = 0; i < failures.length; i++) {
			doThrow(failures[i]).when(service).sync(1);
			String body = mvc.perform(post("/api/admin/parking-sync/operations").param("maxPages", "1"))
					.andExpect(status().is(statuses[i])).andReturn().getResponse().getContentAsString();
			assertFalse(body.contains("fixture-sensitive-marker"));
		}
	}
}
