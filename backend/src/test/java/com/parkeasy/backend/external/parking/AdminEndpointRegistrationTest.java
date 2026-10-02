package com.parkeasy.backend.external.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminEndpointRegistrationTest {
	private final Class<?>[] controllers = {ParkingFacilitySyncController.class, ParkingOperationSyncController.class,
			ParkingRealtimeSyncController.class, PublicDataPageDiagnosticController.class,
			PublicParkingFacilityFullSyncController.class, PublicParkingOperationFullSyncController.class,
			PublicParkingRealtimeFullSyncController.class};

	@Test
	void allAdminControllersAreAbsentByDefaultAndInProdEvenWithOptIn() {
		for (String[] properties : new String[][] {{}, {"parkeasy.admin.enabled=false"},
				{"spring.profiles.active=prod", "parkeasy.admin.enabled=true"}}) {
			new ApplicationContextRunner().withUserConfiguration(controllers).withPropertyValues(properties)
					.run(context -> {
						assertThat(context).hasNotFailed();
						for (Class<?> controller : controllers) assertThat(context).doesNotHaveBean(controller);
					});
		}
	}

	@Test
	void isolatedNonProductionMaintenanceCanRegisterWithoutStartingSync() {
		new ApplicationContextRunner().withUserConfiguration(ParkingOperationSyncController.class)
				.withBean(PublicParkingOperationSyncService.class, () -> mock(PublicParkingOperationSyncService.class))
				.withPropertyValues("parkeasy.admin.enabled=true")
				.run(context -> {
					assertThat(context).hasSingleBean(ParkingOperationSyncController.class);
					org.mockito.Mockito.verifyNoInteractions(context.getBean(PublicParkingOperationSyncService.class));
				});
	}
}
