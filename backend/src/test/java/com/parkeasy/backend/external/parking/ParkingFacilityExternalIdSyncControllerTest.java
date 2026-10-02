package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ParkingFacilityExternalIdSyncControllerTest {
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ParkingFacilitySyncController(
				mock(PublicParkingFacilitySyncService.class), mock(PublicParkingFacilityExternalIdSyncService.class)))
				.setValidator(validator).build();
	}

	@Test
	void rejectsBlankOrOversizedExternalId() throws Exception {
		mockMvc.perform(post("/api/admin/parking-sync/facilities/by-external-id").param("externalId", " "))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/parking-sync/facilities/by-external-id").param("externalId", "x".repeat(101)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsPageAndRowLimits() throws Exception {
		mockMvc.perform(post("/api/admin/parking-sync/facilities/by-external-id")
				.param("externalId", "id").param("maxPages", "101")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/parking-sync/facilities/by-external-id")
				.param("externalId", "id").param("numOfRows", "0")).andExpect(status().isBadRequest());
	}
}
