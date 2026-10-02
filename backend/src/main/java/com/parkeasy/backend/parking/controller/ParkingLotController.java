package com.parkeasy.backend.parking.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.parkeasy.backend.parking.dto.ParkingLotDetailResponse;
import com.parkeasy.backend.parking.dto.ParkingLotSummaryResponse;
import com.parkeasy.backend.parking.dto.NearbyParkingLotResponse;
import com.parkeasy.backend.parking.service.ParkingLotService;
import com.parkeasy.backend.parking.service.NearbyParkingLotFilter;
import com.parkeasy.backend.parking.service.NearbyParkingLotSort;
import com.parkeasy.backend.parking.domain.ParkingType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

	private final ParkingLotService parkingLotService;

	public ParkingLotController(ParkingLotService parkingLotService) {
		this.parkingLotService = parkingLotService;
	}

	@GetMapping
	@Deprecated(since = "2026-09", forRemoval = false)
	public ResponseEntity<List<ParkingLotSummaryResponse>> findAll() {
		return ResponseEntity.ok().header("Deprecation", "@1790035200")
				.body(parkingLotService.findAll());
	}

	@GetMapping("/nearby")
	public ResponseEntity<List<NearbyParkingLotResponse>> findNearby(
			@RequestParam
			@DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") BigDecimal latitude,
			@RequestParam
			@DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") BigDecimal longitude,
			@RequestParam(defaultValue = "2000")
			@Min(1) @Max(10000) int radius,
			@RequestParam(defaultValue = "100")
			@Min(1) @Max(300) int limit,
			@RequestParam(defaultValue = "DISTANCE") NearbyParkingLotSort sort,
			@RequestParam(required = false) ParkingType parkingType,
			@RequestParam(required = false) Boolean free,
			@RequestParam(required = false) Boolean open24Hours,
			@RequestParam(required = false) Boolean realtimeAvailable) {
		if (parkingType == null && free == null && open24Hours == null && realtimeAvailable == null) {
			return ResponseEntity.ok(parkingLotService.findNearby(latitude, longitude, radius,
					NearbyParkingLotFilter.none(), sort, limit));
		}
		return ResponseEntity.ok(parkingLotService.findNearby(latitude, longitude, radius,
				new NearbyParkingLotFilter(parkingType, free, open24Hours, realtimeAvailable), sort, limit));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ParkingLotDetailResponse> findById(@PathVariable Long id) {
		return ResponseEntity.ok(parkingLotService.findById(id));
	}
}
