package com.parkeasy.backend.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parkeasy.sync")
public record ParkingSyncProperties(Facility facility, Operation operation, Realtime realtime) {
	public record Facility(boolean enabled, String cron, int maxPages, String sido, String sigungu) { }
	public record Operation(boolean enabled, String cron, int maxPages) { }
	public record Realtime(boolean enabled, long fixedDelay, long initialDelay,
			int maxPages, long staleAfterMinutes) { }
}
