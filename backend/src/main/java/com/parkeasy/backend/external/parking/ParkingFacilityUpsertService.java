package com.parkeasy.backend.external.parking;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;

@Service
public class ParkingFacilityUpsertService {

	private final ParkingLotRepository parkingLotRepository;

	public ParkingFacilityUpsertService(ParkingLotRepository parkingLotRepository) {
		this.parkingLotRepository = parkingLotRepository;
	}

	@Transactional
	public ParkingFacilityUpsertResult upsert(Collection<ParkingFacilityData> facilities) {
		if (facilities.isEmpty()) return new ParkingFacilityUpsertResult(0, 0);
		List<String> externalIds = facilities.stream().map(ParkingFacilityData::externalId).toList();
		Map<String, ParkingLot> existingByExternalId = new HashMap<>();
		for (ParkingLot parkingLot : parkingLotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, externalIds)) {
			existingByExternalId.put(parkingLot.getExternalId(), parkingLot);
		}
		List<ParkingLot> newParkingLots = new java.util.ArrayList<>();
		int inserted = 0;
		int updated = 0;
		for (ParkingFacilityData facility : facilities) {
			ParkingLot existing = existingByExternalId.get(facility.externalId());
			if (existing == null) {
				newParkingLots.add(ParkingLot.builder()
						.name(facility.name())
						.address(facility.address())
						.latitude(facility.latitude())
						.longitude(facility.longitude())
						.totalSpaces(facility.totalSpaces())
						.dataSource(DataSource.PUBLIC_DATA_PORTAL)
						.externalId(facility.externalId())
						.build());
				inserted++;
			} else {
				existing.updateFacilityInfo(facility.name(), facility.address(), facility.latitude(),
						facility.longitude(), facility.totalSpaces());
				updated++;
			}
		}
		if (!newParkingLots.isEmpty()) parkingLotRepository.saveAll(newParkingLots);
		return new ParkingFacilityUpsertResult(inserted, updated);
	}

	@Transactional
	public ParkingFacilitySingleUpsertResult upsertOne(ParkingFacilityData facility) {
		ParkingLot existing = parkingLotRepository.findByDataSourceAndExternalId(
				DataSource.PUBLIC_DATA_PORTAL, facility.externalId()).orElse(null);
		if (existing == null) {
			ParkingLot saved = parkingLotRepository.save(ParkingLot.builder()
					.name(facility.name()).address(facility.address())
					.latitude(facility.latitude()).longitude(facility.longitude())
					.totalSpaces(facility.totalSpaces()).dataSource(DataSource.PUBLIC_DATA_PORTAL)
					.externalId(facility.externalId()).build());
			return new ParkingFacilitySingleUpsertResult(true, false, saved.getId());
		}
		existing.updateFacilityInfo(facility.name(), facility.address(), facility.latitude(),
				facility.longitude(), facility.totalSpaces());
		return new ParkingFacilitySingleUpsertResult(false, true, existing.getId());
	}
}
