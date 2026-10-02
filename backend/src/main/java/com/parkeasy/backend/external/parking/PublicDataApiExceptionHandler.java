package com.parkeasy.backend.external.parking;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice(basePackages = "com.parkeasy.backend.external.parking")
public class PublicDataApiExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "INVALID_SYNC_REQUEST", "message", "Check sync request and configuration"));
	}

	@ExceptionHandler(org.springframework.web.client.RestClientException.class)
	ResponseEntity<Map<String, String>> handleClientFailure(RuntimeException exception) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(Map.of("error", "PUBLIC_DATA_API_ERROR", "message", "Public data request failed"));
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> handleStateFailure(IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "SYNC_UNAVAILABLE", "message", "Sync request could not be completed"));
	}

	@ExceptionHandler(ResourceAccessException.class)
	ResponseEntity<Map<String, String>> handleTimeout(ResourceAccessException exception) {
		return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
				.body(Map.of("error", "PUBLIC_DATA_API_TIMEOUT", "message", "Public data API did not respond in time"));
	}

	@ExceptionHandler(HttpServerErrorException.class)
	ResponseEntity<Map<String, String>> handleUpstream(HttpServerErrorException exception) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(Map.of("error", "PUBLIC_DATA_API_ERROR", "message", "Public data API returned an error"));
	}
}
