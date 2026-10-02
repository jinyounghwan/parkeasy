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

/** Development-only endpoints. Add administrator authentication before production use. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "parkeasy.admin.enabled", havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
@RequestMapping("/api/admin/parking-sync/facilities/full")
public class PublicParkingFacilityFullSyncController {
	private final PublicParkingFacilityFullSyncService service;

	public PublicParkingFacilityFullSyncController(PublicParkingFacilityFullSyncService service) {
		this.service = service;
	}

	@PostMapping("/start")
	public ResponseEntity<ParkingFacilityFullSyncStatus> start(
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "1") int startPage,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.start(maxPages, startPage, full, confirm));
	}

	@PostMapping("/resume")
	public ResponseEntity<ParkingFacilityFullSyncStatus> resume(
			@RequestParam(required = false) Long jobId,
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.resume(jobId, maxPages, full, confirm));
	}

	@PostMapping("/pause")
	public ParkingFacilityFullSyncStatus pause() { return service.requestPause(); }

	@PostMapping("/repair")
	public ParkingFacilityFullSyncStatus repair(@RequestParam long jobId) {
		return service.repairPrematureCompletion(jobId);
	}

	@GetMapping("/status")
	public ParkingFacilityFullSyncStatus status(@RequestParam(required = false) Long jobId) {
		return service.status(jobId);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "INVALID_FULL_SYNC_REQUEST", "message", "Check sync limits, job state and confirmation"));
	}
}
