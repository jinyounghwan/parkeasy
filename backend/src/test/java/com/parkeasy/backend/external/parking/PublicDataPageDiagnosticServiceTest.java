package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import tools.jackson.databind.json.JsonMapper;

class PublicDataPageDiagnosticServiceTest {
	private final PublicDataApiCaller apiCaller = mock(PublicDataApiCaller.class);
	private final PublicDataPageDiagnosticService service = new PublicDataPageDiagnosticService(
			apiCaller, JsonMapper.builder().build(), "http://example.test/facilities", "encoded-key");

	@Test
	void callsEachRequestedPageOnceWithoutRetry() {
		PublicParkingFacilityResponse response = response("0");
		when(apiCaller.getOnce(any(), any())).thenReturn(
				new PublicDataApiCaller.SingleCallResult<>(200, response));

		PublicDataPagesDiagnosticResponse diagnostic = service.diagnose(3, 100, 0);

		assertEquals(3, diagnostic.results().size());
		assertTrue(diagnostic.results().stream().allMatch(PublicDataPageDiagnosticResult::success));
		verify(apiCaller, times(3)).getOnce(any(), any());
	}

	@Test
	void stopsImmediatelyAfterTimeoutWithoutRetryingOrCallingLaterPages() {
		when(apiCaller.getOnce(any(), any()))
				.thenReturn(new PublicDataApiCaller.SingleCallResult<>(200, response("0")))
				.thenThrow(new ResourceAccessException("timeout"));

		PublicDataPagesDiagnosticResponse diagnostic = service.diagnose(3, 100, 0);

		assertEquals(2, diagnostic.results().size());
		assertTrue(diagnostic.results().get(0).success());
		assertFalse(diagnostic.results().get(1).success());
		assertEquals(2, diagnostic.results().get(1).pageNo());
		verify(apiCaller, times(2)).getOnce(any(), any());
	}

	private PublicParkingFacilityResponse response(String resultCode) {
		return new PublicParkingFacilityResponse(List.of(), "1", resultCode, "0", "100", "SUCCESS");
	}
}
