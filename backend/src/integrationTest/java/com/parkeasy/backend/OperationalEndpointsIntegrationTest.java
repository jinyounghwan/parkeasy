package com.parkeasy.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("parking-repository-integration")
@SpringBootTest(properties = "parkeasy.cors.allowed-origins=https://parking.example.test")
class OperationalEndpointsIntegrationTest {
	@Autowired WebApplicationContext context;

	@Test
	void publicProbesAndDisabledAdminRoutes() throws Exception {
		var mvc = MockMvcBuilders.webAppContextSetup(context).build();
		mvc.perform(get("/api/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/api/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(post("/api/admin/parking-sync/operations")).andExpect(status().isNotFound());
		mvc.perform(get("/api/admin/public-data/diagnostic/pages")).andExpect(status().isNotFound());
		assertFalse(context.getEnvironment().getProperty("parkeasy.sync.facility.enabled", Boolean.class));
		assertFalse(context.getEnvironment().getProperty("parkeasy.sync.operation.enabled", Boolean.class));
		assertFalse(context.getEnvironment().getProperty("parkeasy.sync.realtime.enabled", Boolean.class));
	}

	@Test
	void corsAllowsOnlyConfiguredReadOrigin() throws Exception {
		var mvc = MockMvcBuilders.webAppContextSetup(context).build();
		mvc.perform(options("/api/parking-lots/nearby").header("Origin", "https://parking.example.test")
				.header("Access-Control-Request-Method", "GET")).andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://parking.example.test"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		mvc.perform(options("/api/parking-lots/nearby").header("Origin", "https://untrusted.example.test")
				.header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
		mvc.perform(options("/api/parking-lots/nearby").header("Origin", "https://parking.example.test")
				.header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
	}
}
