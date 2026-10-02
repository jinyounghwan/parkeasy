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
@RequestMapping("/api/admin/parking-sync/realtime/full")
public class PublicParkingRealtimeFullSyncController {
	private final PublicParkingRealtimeFullSyncService service;
	public PublicParkingRealtimeFullSyncController(PublicParkingRealtimeFullSyncService service) {
		this.service = service;
	}
	@PostMapping("/start")
	public ResponseEntity<ParkingRealtimeFullSyncStatus> start(
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "1") int startPage,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.start(maxPages, startPage, full, confirm));
	}
	@PostMapping("/resume")
	public ResponseEntity<ParkingRealtimeFullSyncStatus> resume(
			@RequestParam(required = false) Long jobId,
			@RequestParam(required = false) Integer maxPages,
			@RequestParam(defaultValue = "false") boolean full,
			@RequestParam(required = false) String confirm) {
		return ResponseEntity.accepted().body(service.resume(jobId, maxPages, full, confirm));
	}
	@PostMapping("/pause")
	public ParkingRealtimeFullSyncStatus pause() { return service.pause(); }
	@GetMapping("/status")
	public ParkingRealtimeFullSyncStatus status(@RequestParam(required = false) Long jobId) {
		return service.status(jobId);
	}
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "INVALID_REALTIME_FULL_SYNC_REQUEST", "message", "Check sync limits, job state and confirmation"));
	}
}
