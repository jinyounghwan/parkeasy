package com.parkeasy.backend.external.parking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Development-only manual endpoint. Protect or remove this endpoint before production. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "parkeasy.admin.enabled", havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
@RequestMapping("/api/admin/parking-sync")
public class ParkingRealtimeSyncController {
	private final PublicParkingRealtimeSyncService syncService;
	public ParkingRealtimeSyncController(PublicParkingRealtimeSyncService syncService) { this.syncService = syncService; }
	@PostMapping("/realtime")
	public ResponseEntity<ParkingRealtimeSyncResult> syncRealtime(
			@RequestParam(defaultValue = "1") @Min(1) @Max(100) int maxPages) {
		return ResponseEntity.ok(syncService.sync(maxPages));
	}
}
