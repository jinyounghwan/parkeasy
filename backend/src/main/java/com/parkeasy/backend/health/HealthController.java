package com.parkeasy.backend.health;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public probes intentionally expose no credentials, configuration, or worker details. */
@RestController
@RequestMapping("/api/health")
public class HealthController {
	private final ObjectProvider<DataSource> dataSources;
	public HealthController(ObjectProvider<DataSource> dataSources) { this.dataSources = dataSources; }

	@GetMapping("/live")
	public Map<String, String> live() { return Map.of("status", "UP"); }

	@GetMapping("/ready")
	public ResponseEntity<Map<String, String>> ready() {
		DataSource source = dataSources.getIfAvailable();
		if (source != null) {
			try (var connection = source.getConnection()) {
				connection.setReadOnly(true);
				connection.setAutoCommit(false);
				try (var statement = connection.createStatement()) {
					statement.setQueryTimeout(2);
					try (var result = statement.executeQuery("SELECT 1")) {
						if (result.next() && result.getInt(1) == 1) return ResponseEntity.ok(Map.of("status", "UP"));
					}
				} finally { connection.rollback(); }
			} catch (SQLException exception) {
				// Do not expose connection details or exception messages in probes/logs.
			}
		}
		return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
	}
}
