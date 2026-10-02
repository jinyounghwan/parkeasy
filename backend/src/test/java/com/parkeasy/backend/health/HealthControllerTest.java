package com.parkeasy.backend.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class HealthControllerTest {
	@SuppressWarnings("unchecked")
	private final ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
	private final HealthController controller = new HealthController(provider);

	@Test
	void livenessDoesNotTouchDatabaseAndMissingDatabaseIsNotReady() {
		assertEquals("UP", controller.live().get("status")); verifyNoInteractions(provider);
		assertEquals(503, controller.ready().getStatusCode().value());
	}

	@Test
	void databaseFailureIs503WithoutExceptionDetails() throws Exception {
		DataSource source = mock(DataSource.class); when(provider.getIfAvailable()).thenReturn(source);
		when(source.getConnection()).thenThrow(new SQLException("sensitive-test-marker"));
		var result = controller.ready();
		assertEquals(503, result.getStatusCode().value());
		assertEquals(java.util.Map.of("status", "DOWN"), result.getBody());
	}

	@Test
	void readinessUsesReadOnlySelectWithRollback() throws Exception {
		DataSource source = mock(DataSource.class); Connection c = mock(Connection.class);
		Statement s = mock(Statement.class); ResultSet r = mock(ResultSet.class);
		when(provider.getIfAvailable()).thenReturn(source); when(source.getConnection()).thenReturn(c);
		when(c.createStatement()).thenReturn(s); when(s.executeQuery("SELECT 1")).thenReturn(r);
		when(r.next()).thenReturn(true); when(r.getInt(1)).thenReturn(1);
		assertEquals(200, controller.ready().getStatusCode().value());
		verify(c).setReadOnly(true); verify(c).setAutoCommit(false); verify(s).setQueryTimeout(2);
		verify(c).rollback(); verify(c).close();
	}
}
