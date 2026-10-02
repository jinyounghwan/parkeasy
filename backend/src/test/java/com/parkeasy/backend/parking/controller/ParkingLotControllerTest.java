package com.parkeasy.backend.parking.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingType;
import com.parkeasy.backend.parking.dto.ParkingLotDetailResponse;
import com.parkeasy.backend.parking.dto.ParkingLotSummaryResponse;
import com.parkeasy.backend.parking.dto.NearbyParkingLotResponse;
import com.parkeasy.backend.parking.service.ParkingLotNotFoundException;
import com.parkeasy.backend.parking.service.ParkingLotService;
import com.parkeasy.backend.parking.service.NearbyParkingLotFilter;
import com.parkeasy.backend.parking.service.NearbyParkingLotSort;

class ParkingLotControllerTest {

	private ParkingLotService parkingLotService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		parkingLotService = org.mockito.Mockito.mock(ParkingLotService.class);
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ParkingLotController(parkingLotService))
				.setValidator(validator)
				.build();
	}

	@Test
	void returnsParkingLotList() throws Exception {
		ParkingLotSummaryResponse response = new ParkingLotSummaryResponse(
				1L, "수원시청 테스트 공영주차장", "테스트 주소",
				new BigDecimal("37.2635737"), new BigDecimal("127.0286009"),
				ParkingType.PUBLIC, 120, 30, 1000, false, false);
		when(parkingLotService.findAll()).thenReturn(List.of(response));

		mockMvc.perform(get("/api/parking-lots"))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
						.string("Deprecation", "@1790035200"))
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("수원시청 테스트 공영주차장"))
				.andExpect(jsonPath("$[0].isFree").value(false));
	}

	@Test
	void returnsParkingLotDetail() throws Exception {
		ParkingLotDetailResponse response = new ParkingLotDetailResponse(
				1L, "수원시청 테스트 공영주차장", "테스트 주소",
				new BigDecimal("37.2635737"), new BigDecimal("127.0286009"),
				ParkingType.PUBLIC, 120, 30, 1000, false, false,
				10, 500, 10000, LocalTime.of(6, 0), LocalTime.of(23, 0),
				DataSource.PUBLIC_DATA_PORTAL, null, null);
		when(parkingLotService.findById(1L)).thenReturn(response);

		mockMvc.perform(get("/api/parking-lots/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.additionalFee").value(500))
				.andExpect(jsonPath("$.dataSource").value("PUBLIC_DATA_PORTAL"))
				.andExpect(jsonPath("$.externalId").doesNotExist());
	}

	@Test
	void returnsNotFoundWhenParkingLotDoesNotExist() throws Exception {
		when(parkingLotService.findById(999L)).thenThrow(new ParkingLotNotFoundException(999L));

		mockMvc.perform(get("/api/parking-lots/999"))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnsNearbyParkingLots() throws Exception {
		NearbyParkingLotResponse response = new NearbyParkingLotResponse(
				1L, "nearby", "address",
				new BigDecimal("37.2635737"), new BigDecimal("127.0286009"), 12L,
				ParkingType.PUBLIC, 120, 30, 1000, false, false, null, null);
		when(parkingLotService.findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000,
				NearbyParkingLotFilter.none(), NearbyParkingLotSort.DISTANCE, 100))
				.thenReturn(List.of(response));

		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286")
					.param("radius", "2000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].distanceMeters").value(12));
	}

	@Test
	void returnsBadRequestForLatitudeOutsideRange() throws Exception {
		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "90.1")
					.param("longitude", "127.0286"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsBadRequestForLongitudeOutsideRange() throws Exception {
		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "180.1"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsBadRequestForRadiusOutsideRange() throws Exception {
		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286")
					.param("radius", "10001"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsLimitOutsideRange() throws Exception {
		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286")
					.param("limit", "301"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void usesDefaultRadiusWhenRadiusIsOmitted() throws Exception {
		when(parkingLotService.findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000,
				NearbyParkingLotFilter.none(), NearbyParkingLotSort.DISTANCE, 100))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286"))
				.andExpect(status().isOk());

		verify(parkingLotService).findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 2000,
				NearbyParkingLotFilter.none(), NearbyParkingLotSort.DISTANCE, 100);
	}

	@Test
	void passesCombinedNearbyFiltersToService() throws Exception {
		when(parkingLotService.findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 1000,
				new NearbyParkingLotFilter(ParkingType.PUBLIC, true, true, true),
				NearbyParkingLotSort.AVAILABLE, 25))
				.thenReturn(List.of());

		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286")
					.param("radius", "1000")
					.param("parkingType", "PUBLIC")
					.param("free", "true")
					.param("open24Hours", "true")
					.param("realtimeAvailable", "true")
					.param("sort", "AVAILABLE")
					.param("limit", "25"))
				.andExpect(status().isOk());

		verify(parkingLotService).findNearby(
				new BigDecimal("37.2636"), new BigDecimal("127.0286"), 1000,
				new NearbyParkingLotFilter(ParkingType.PUBLIC, true, true, true),
				NearbyParkingLotSort.AVAILABLE, 25);
	}

	@Test
	void rejectsUnknownParkingType() throws Exception {
		mockMvc.perform(get("/api/parking-lots/nearby")
					.param("latitude", "37.2636")
					.param("longitude", "127.0286")
					.param("parkingType", "UNKNOWN"))
				.andExpect(status().isBadRequest());
	}
}
