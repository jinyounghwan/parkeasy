package com.parkeasy.backend.external.parking;

record ParkingFacilitySingleUpsertResult(boolean inserted, boolean updated, Long parkingLotId) { }
