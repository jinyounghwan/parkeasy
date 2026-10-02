package com.parkeasy.backend.external.parking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Development-only manual endpoint. Protect or remove this endpoint before production. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "parkeasy.admin.enabled", havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
@RequestMapping("/api/admin/parking-sync")
public class ParkingFacilitySyncController {

	private final PublicParkingFacilitySyncService syncService;
	private final PublicParkingFacilityExternalIdSyncService externalIdSyncService;

	public ParkingFacilitySyncController(PublicParkingFacilitySyncService syncService,
			PublicParkingFacilityExternalIdSyncService externalIdSyncService) {
		this.syncService = syncService;
		this.externalIdSyncService = externalIdSyncService;
	}

	@PostMapping("/facilities")
	public ResponseEntity<ParkingFacilitySyncResult> syncFacilities(
			@RequestParam @NotBlank String sido,
			@RequestParam @NotBlank String sigungu,
			@RequestParam(defaultValue = "5") @Min(1) @Max(100) int maxPages) {
		return ResponseEntity.ok(syncService.sync(sido, sigungu, maxPages));
	}

	@PostMapping("/facilities/by-external-id")
	public ResponseEntity<ParkingFacilityExternalIdSyncResult> syncFacilityByExternalId(
			@RequestParam @NotBlank @Size(max = 100) String externalId,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int maxPages,
			@RequestParam(defaultValue = "100") @Min(1) @Max(100) int numOfRows) {
		return ResponseEntity.ok(externalIdSyncService.syncByExternalId(externalId, maxPages, numOfRows));
	}
}
