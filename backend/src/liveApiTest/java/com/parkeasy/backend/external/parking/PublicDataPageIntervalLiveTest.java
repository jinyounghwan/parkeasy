package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.json.JsonMapper;

class PublicDataPageIntervalLiveTest {
	private static final String ENDPOINT =
			"http://apis.data.go.kr/B553881/Parking/PrkSttusInfo";
	private static final int NUM_OF_ROWS = 100;
	private static final long PAGE_INTERVAL_MS = 1_000L;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void fetchesThreePagesWithOneSecondIntervals() throws Exception {
		for (int pageNo = 1; pageNo <= 3; pageNo++) {
			fetchAndVerify(pageNo);
			if (pageNo < 3) {
				Thread.sleep(PAGE_INTERVAL_MS);
			}
		}
	}

	private void fetchAndVerify(int pageNo) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(buildUri(pageNo))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		long startedNanos = System.nanoTime();

		try {
			HttpResponse<String> httpResponse = httpClient.send(
					request, HttpResponse.BodyHandlers.ofString());
			PublicParkingFacilityResponse response = jsonMapper.readValue(
					httpResponse.body(), PublicParkingFacilityResponse.class);
			long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

			System.out.printf(
					"PAGE_INTERVAL_TEST pageNo=%d durationMs=%d status=%d resultCode=%s items=%d%n",
					pageNo, durationMs, httpResponse.statusCode(), response.resultCode(),
					response.safeItems().size());

			assertEquals(200, httpResponse.statusCode());
			assertEquals("0", response.resultCode());
		} catch (Exception exception) {
			long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
			System.out.printf(
					"PAGE_INTERVAL_TEST pageNo=%d durationMs=%d status=N/A resultCode=N/A items=N/A%n",
					pageNo, durationMs);
			throw exception;
		}
	}

	private URI buildUri(int pageNo) {
		return UriComponentsBuilder.fromUriString(ENDPOINT)
				.queryParam("serviceKey", System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"))
				.queryParam("numOfRows", NUM_OF_ROWS)
				.queryParam("format", 2)
				.queryParam("pageNo", pageNo)
				.build(true)
				.toUri();
	}
}
