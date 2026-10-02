package com.parkeasy.backend.external.parking;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class FullSyncAlreadyRunningException extends RuntimeException {
	public FullSyncAlreadyRunningException() { super("Facility full sync is already running"); }
}
