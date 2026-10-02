package com.parkeasy.backend.external.parking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ParkingFullSyncWorkerConfiguration {
	@Bean(name = "parkingFullSyncTaskExecutor")
	ThreadPoolTaskExecutor parkingFullSyncTaskExecutor() {
		return singleThreadExecutor("parking-full-sync-");
	}

	@Bean(name = "parkingOperationFullSyncTaskExecutor")
	ThreadPoolTaskExecutor parkingOperationFullSyncTaskExecutor() {
		return singleThreadExecutor("parking-operation-full-sync-");
	}

	@Bean(name = "parkingRealtimeFullSyncTaskExecutor")
	ThreadPoolTaskExecutor parkingRealtimeFullSyncTaskExecutor() {
		return singleThreadExecutor("parking-realtime-full-sync-");
	}

	private ThreadPoolTaskExecutor singleThreadExecutor(String threadNamePrefix) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();
		return executor;
	}
}
