package com.parkeasy.backend.external.parking;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PublicDataPageDiagnosticService {
	private final PublicDataApiCaller apiCaller;
	private final JsonMapper jsonMapper;
	private final String endpoint;
	private final String serviceKey;

	@Autowired
	public PublicDataPageDiagnosticService(
			@Value("${parkeasy.public-data.parking-facility-url:http://apis.data.go.kr/B553881/Parking/PrkSttusInfo}") String endpoint,
			@Value("${parkeasy.public-data.service-key:}") String serviceKey) {
		this(PublicDataApiCaller.createDefault(), JsonMapper.builder().build(), endpoint, serviceKey);
	}

	PublicDataPageDiagnosticService(PublicDataApiCaller apiCaller, JsonMapper jsonMapper,
			String endpoint, String serviceKey) {
		this.apiCaller = apiCaller;
		this.jsonMapper = jsonMapper;
		this.endpoint = endpoint;
		this.serviceKey = serviceKey;
	}

	public PublicDataPagesDiagnosticResponse diagnose(int pages, int numOfRows, long delayMs) {
		if (!StringUtils.hasText(serviceKey)) {
			throw new IllegalStateException("PARKEASY_PUBLIC_DATA_SERVICE_KEY is not configured");
		}

		List<PublicDataPageDiagnosticResult> results = new ArrayList<>();
		for (int pageNo = 1; pageNo <= pages; pageNo++) {
			PublicDataPageDiagnosticResult result = fetchOnce(pageNo, numOfRows);
			results.add(result);
			if (!result.success()) break;
			if (pageNo < pages) waitBeforeNextPage(delayMs);
		}
		return new PublicDataPagesDiagnosticResponse(List.copyOf(results));
	}

	private PublicDataPageDiagnosticResult fetchOnce(int pageNo, int numOfRows) {
		long startedNanos = System.nanoTime();
		try {
			PublicDataApiCaller.SingleCallResult<PublicParkingFacilityResponse> call =
					apiCaller.getOnce(buildUri(pageNo, numOfRows), this::parseResponse);
			PublicParkingFacilityResponse response = call.body();
			boolean success = call.status() == 200 && "0".equals(response.resultCode());
			return new PublicDataPageDiagnosticResult(pageNo, elapsedMillis(startedNanos), success,
					call.status(), response.resultCode(), response.safeItems().size());
		} catch (RuntimeException exception) {
			Integer status = exception instanceof HttpStatusCodeException statusException
					? statusException.getStatusCode().value() : null;
			return new PublicDataPageDiagnosticResult(
					pageNo, elapsedMillis(startedNanos), false, status, null, 0);
		}
	}

	private PublicParkingFacilityResponse parseResponse(String responseBody) {
		if (!StringUtils.hasText(responseBody)) {
			throw new IllegalStateException("Public parking API returned an empty response");
		}
		try {
			return jsonMapper.readValue(responseBody, PublicParkingFacilityResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Public parking API returned a non-JSON response", exception);
		}
	}

	private URI buildUri(int pageNo, int numOfRows) {
		return UriComponentsBuilder.fromUriString(endpoint)
				.queryParam("serviceKey", serviceKey)
				.queryParam("numOfRows", numOfRows)
				.queryParam("pageNo", pageNo)
				.queryParam("format", 2)
				.build(true)
				.toUri();
	}

	private void waitBeforeNextPage(long delayMs) {
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Public data diagnostic was interrupted", exception);
		}
	}

	private long elapsedMillis(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}
}
