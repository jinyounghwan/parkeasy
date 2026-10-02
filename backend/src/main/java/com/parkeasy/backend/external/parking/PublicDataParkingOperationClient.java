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
public class PublicDataParkingOperationClient implements PublicParkingOperationClient {
	private final PublicDataApiCaller apiCaller;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final String endpoint;
	private final String serviceKey;

	@Autowired
	public PublicDataParkingOperationClient(
			@Value("${parkeasy.public-data.parking-operation-url:http://apis.data.go.kr/B553881/Parking/PrkOprInfo}") String endpoint,
			@Value("${parkeasy.public-data.service-key:}") String serviceKey,
			@Value("${parkeasy.public-data.retry-backoff-ms:2000}") long retryBackoffMs) {
		apiCaller = PublicDataApiCaller.createDefault(retryBackoffMs);
		this.endpoint = endpoint;
		this.serviceKey = serviceKey;
	}

	public PublicDataParkingOperationClient(String endpoint, String serviceKey) {
		this(endpoint, serviceKey, 0);
	}

	@Override
	public PublicParkingOperationResponse fetchOperations(int pageNo, int numOfRows) {
		if (!StringUtils.hasText(serviceKey)) throw new IllegalStateException("PARKEASY_PUBLIC_DATA_SERVICE_KEY is not configured");
		String body = apiCaller.get(buildUri(pageNo, numOfRows));
		if (!StringUtils.hasText(body)) throw new IllegalStateException("Public parking operation API returned an empty response");
		try {
			PublicParkingOperationResponse response = jsonMapper.readValue(body, PublicParkingOperationResponse.class);
			if (!"0".equals(response.resultCode())) {
				throw new IllegalStateException("Public parking operation API failed: " + response.resultCode()
						+ " (" + response.resultMsg() + ")");
			}
			return response;
		} catch (JacksonException exception) {
			throw new IllegalStateException("Public parking operation API returned a non-JSON response", exception);
		}
	}

	URI buildUri(int pageNo, int numOfRows) {
		return UriComponentsBuilder.fromUriString(endpoint)
				.queryParam("serviceKey", serviceKey).queryParam("numOfRows", numOfRows)
				.queryParam("pageNo", pageNo).queryParam("format", 2).build(true).toUri();
	}
}
