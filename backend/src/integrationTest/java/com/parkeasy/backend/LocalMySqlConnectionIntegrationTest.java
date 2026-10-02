package com.parkeasy.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("local")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PARKEASY_MYSQL_TEST_ENABLED", matches = "true")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
		"spring.datasource.hikari.read-only=true", "parkeasy.admin.enabled=false",
		"parkeasy.sync.facility.enabled=false", "parkeasy.sync.operation.enabled=false", "parkeasy.sync.realtime.enabled=false"})
class LocalMySqlConnectionIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void connectsToLocalMySql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertTrue(connection.isValid(2));
		}
	}
}
