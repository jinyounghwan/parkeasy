package com.parkeasy.backend.external.parking;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Development-only endpoints. Administrator authentication is required before production use. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "parkeasy.admin.enabled", havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
@RequestMapping("/api/admin/parking-sync/operations/full")
public class PublicParkingOperationFullSyncController {
	private final PublicParkingOperationFullSyncService service;

	public PublicParkingOperationFullSyncController(PublicParkingOperationFullSyncService service) {
		this.service = service;
	}

	@PostMapping("/start")
	public ResponseEntity<ParkingOperationFullSyncStatus> start(
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "1") int startPage,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.start(maxPages, startPage, full, confirm));
	}

	@PostMapping("/resume")
	public ResponseEntity<ParkingOperationFullSyncStatus> resume(
			@RequestParam(required = false) Long jobId,
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.resume(jobId, maxPages, full, confirm));
	}

	@PostMapping("/pause")
	public ParkingOperationFullSyncStatus pause() { return service.pause(); }

	@GetMapping("/status")
	public ParkingOperationFullSyncStatus status(@RequestParam(required = false) Long jobId) {
		return service.status(jobId);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "INVALID_OPERATION_FULL_SYNC_REQUEST", "message", "Check sync limits, job state and confirmation"));
	}
}
