package com.parkeasy.backend.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ParkingWebConfigurationTest {
	@Test
	void rejectsWildcardsPathsAndNonOriginsWithoutEchoingInput() {
		for (String origin : new String[] {"*", "https://*.example.test", "https://example.test/path",
				"https://user:do-not-echo@example.test", "https://example.test?key=do-not-echo", "not-an-origin"}) {
			var error = assertThrows(IllegalArgumentException.class, () -> new ParkingWebConfiguration(origin));
			assertFalse(error.getMessage().contains("do-not-echo"));
		}
	}

	@Test
	void acceptsEmptySameOriginPolicyAndExactAllowlist() {
		assertDoesNotThrow(() -> new ParkingWebConfiguration(""));
		assertDoesNotThrow(() -> new ParkingWebConfiguration("http://localhost:5173, https://parking.example.test"));
	}
}
