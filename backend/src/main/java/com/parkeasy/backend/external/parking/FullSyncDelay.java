package com.parkeasy.backend.external.parking;

import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Component;

@Component
public class FullSyncDelay {
	boolean await(long delayMs, BooleanSupplier stopRequested) {
		long deadline = System.nanoTime() + delayMs * 1_000_000L;
		while (!stopRequested.getAsBoolean()) {
			long remainingMs = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
			if (remainingMs == 0) return true;
			try {
				Thread.sleep(Math.min(remainingMs, 250));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Facility full sync delay was interrupted", exception);
			}
		}
		return false;
	}
}
