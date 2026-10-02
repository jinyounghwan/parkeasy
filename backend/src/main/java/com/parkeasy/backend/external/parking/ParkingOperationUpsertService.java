package com.parkeasy.backend.external.parking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingOperation;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingOperationRepository;

@Service
public class ParkingOperationUpsertService {
	private final ParkingLotRepository parkingLotRepository;
	private final ParkingOperationRepository operationRepository;

	public ParkingOperationUpsertService(ParkingLotRepository parkingLotRepository,
			ParkingOperationRepository operationRepository) {
		this.parkingLotRepository = parkingLotRepository;
		this.operationRepository = operationRepository;
	}

	@Transactional
	public ParkingOperationUpsertResult upsert(Collection<ParkingOperationCandidate> candidates) {
		int matched = 0, inserted = 0, updated = 0, skipped = 0;
		for (ParkingOperationCandidate candidate : candidates) {
			ParkingLot parkingLot = parkingLotRepository.findByDataSourceAndExternalId(
					DataSource.PUBLIC_DATA_PORTAL, candidate.externalId()).orElse(null);
			if (parkingLot == null) { skipped++; continue; }
			matched++;
			ParkingOperation operation = operationRepository.findByParkingLotId(parkingLot.getId()).orElse(null);
			if (operation == null) {
				operationRepository.save(new ParkingOperation(parkingLot, candidate.details()));
				inserted++;
			} else {
				operation.updateOperationInfo(candidate.details());
				updated++;
			}
		}
		return new ParkingOperationUpsertResult(matched, inserted, updated, skipped);
	}

	@Transactional
	public ParkingOperationFullUpsertResult upsertBatch(Collection<ParkingOperationCandidate> candidates) {
		List<String> externalIds = candidates.stream().map(ParkingOperationCandidate::externalId).toList();
		Map<String, ParkingLot> lotsByExternalId = new LinkedHashMap<>();
		for (ParkingLot lot : parkingLotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, externalIds)) {
			lotsByExternalId.put(lot.getExternalId(), lot);
		}
		List<Long> lotIds = lotsByExternalId.values().stream().map(ParkingLot::getId).toList();
		Map<Long, ParkingOperation> existingByLotId = new LinkedHashMap<>();
		if (!lotIds.isEmpty()) {
			for (ParkingOperation operation : operationRepository.findByParkingLotIdIn(lotIds)) {
				existingByLotId.put(operation.getParkingLot().getId(), operation);
			}
		}
		int matched = 0, inserted = 0, updated = 0, orphan = 0;
		List<ParkingOperation> writes = new java.util.ArrayList<>();
		for (ParkingOperationCandidate candidate : candidates) {
			ParkingLot lot = lotsByExternalId.get(candidate.externalId());
			if (lot == null) { orphan++; continue; }
			matched++;
			ParkingOperation operation = existingByLotId.get(lot.getId());
			if (operation == null) {
				operation = new ParkingOperation(lot, candidate.details());
				inserted++;
			} else {
				operation.updateOperationInfo(candidate.details());
				updated++;
			}
			writes.add(operation);
		}
		operationRepository.saveAll(writes);
		return new ParkingOperationFullUpsertResult(matched, inserted, updated, orphan);
	}
}
