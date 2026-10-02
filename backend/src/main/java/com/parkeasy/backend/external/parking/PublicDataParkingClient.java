package com.parkeasy.backend.external.parking;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PublicDataParkingClient implements PublicParkingClient {

	private final PublicDataApiCaller apiCaller;
	private final JsonMapper jsonMapper;
	private final String endpoint;
	private final String serviceKey;

	@Autowired
	public PublicDataParkingClient(
			@Value("${parkeasy.public-data.parking-facility-url:http://apis.data.go.kr/B553881/Parking/PrkSttusInfo}") String endpoint,
			@Value("${parkeasy.public-data.service-key:}") String serviceKey,
			@Value("${parkeasy.public-data.retry-backoff-ms:2000}") long retryBackoffMs) {
		this.apiCaller = PublicDataApiCaller.createDefault(retryBackoffMs);
		this.jsonMapper = JsonMapper.builder().build();
		this.endpoint = endpoint;
		this.serviceKey = serviceKey;
	}

	public PublicDataParkingClient(String endpoint, String serviceKey) {
		this(endpoint, serviceKey, 0);
	}

	@Override
	public PublicParkingFacilityResponse fetchFacilities(int pageNo, int numOfRows) {
		if (!StringUtils.hasText(serviceKey)) {
			throw new IllegalStateException("PARKEASY_PUBLIC_DATA_SERVICE_KEY is not configured");
		}
		URI uri = buildUri(pageNo, numOfRows);
		PublicParkingFacilityResponse response = apiCaller.get(uri, pageNo,
				this::parseResponse, parsed -> parsed.safeItems().size());
		validateResponse(response);
		return response;
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

	private void validateResponse(PublicParkingFacilityResponse response) {
		if (!"0".equals(response.resultCode())) {
			throw new IllegalStateException("Public parking API failed: " + response.resultCode()
					+ " (" + response.resultMsg() + ")");
		}
	}

	URI buildUri(int pageNo, int numOfRows) {
		return UriComponentsBuilder.fromUriString(endpoint)
				.queryParam("serviceKey", serviceKey)
				.queryParam("numOfRows", numOfRows)
				.queryParam("pageNo", pageNo)
				.queryParam("format", 2)
				.build(true)
				.toUri();
	}
}
