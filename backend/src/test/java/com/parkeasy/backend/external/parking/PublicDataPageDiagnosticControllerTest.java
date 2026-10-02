package com.parkeasy.backend.external.parking;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PublicDataPageDiagnosticControllerTest {
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(
				new PublicDataPageDiagnosticController(mock(PublicDataPageDiagnosticService.class)))
				.setValidator(validator)
				.build();
	}

	@Test
	void acceptsRequestedDiagnosticLimits() throws Exception {
		mockMvc.perform(get("/api/admin/public-data/diagnostic/pages")
				.param("pages", "3").param("numOfRows", "100").param("delayMs", "1000"))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsRequestsBeyondDiagnosticLimits() throws Exception {
		mockMvc.perform(get("/api/admin/public-data/diagnostic/pages").param("pages", "4"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/admin/public-data/diagnostic/pages").param("numOfRows", "101"))
				.andExpect(status().isBadRequest());
	}
}
