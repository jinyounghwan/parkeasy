package com.parkeasy.backend.external.parking;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parkeasy.backend.parking.domain.DataSource;
import com.parkeasy.backend.parking.domain.ParkingLot;
import com.parkeasy.backend.parking.domain.ParkingRealtime;
import com.parkeasy.backend.parking.repository.ParkingLotRepository;
import com.parkeasy.backend.parking.repository.ParkingRealtimeRepository;

@Service
public class ParkingRealtimeUpsertService {
	private final ParkingLotRepository lotRepository;
	private final ParkingRealtimeRepository realtimeRepository;
	public ParkingRealtimeUpsertService(ParkingLotRepository lotRepository, ParkingRealtimeRepository realtimeRepository) {
		this.lotRepository = lotRepository; this.realtimeRepository = realtimeRepository;
	}

	@Transactional
	public ParkingRealtimeUpsertResult upsert(Collection<ParkingRealtimeCandidate> candidates) {
		int matched = 0, inserted = 0, updated = 0, skipped = 0;
		for (ParkingRealtimeCandidate candidate : candidates) {
			ParkingLot lot = lotRepository.findByDataSourceAndExternalId(
					DataSource.PUBLIC_DATA_PORTAL, candidate.externalId()).orElse(null);
			if (lot == null) { skipped++; continue; }
			matched++;
			ParkingRealtime realtime = realtimeRepository.findByParkingLotId(lot.getId()).orElse(null);
			if (realtime == null) {
				realtimeRepository.save(new ParkingRealtime(lot, candidate.totalSpaces(), candidate.availableSpaces(),
						null, candidate.receivedAt())); inserted++;
			} else {
				realtime.updateStatus(candidate.totalSpaces(), candidate.availableSpaces(), null, candidate.receivedAt()); updated++;
			}
		}
		return new ParkingRealtimeUpsertResult(matched, inserted, updated, skipped);
	}

	@Transactional
	public ParkingRealtimeFullUpsertResult upsertBatch(Collection<ParkingRealtimeCandidate> candidates) {
		List<String> externalIds = candidates.stream().map(ParkingRealtimeCandidate::externalId).toList();
		Map<String, ParkingLot> lotsByExternalId = new LinkedHashMap<>();
		for (ParkingLot lot : lotRepository.findByDataSourceAndExternalIdIn(
				DataSource.PUBLIC_DATA_PORTAL, externalIds)) lotsByExternalId.put(lot.getExternalId(), lot);
		List<Long> lotIds = lotsByExternalId.values().stream().map(ParkingLot::getId).toList();
		Map<Long, ParkingRealtime> existingByLotId = new LinkedHashMap<>();
		if (!lotIds.isEmpty()) {
			for (ParkingRealtime realtime : realtimeRepository.findByParkingLotIdIn(lotIds)) {
				existingByLotId.put(realtime.getParkingLot().getId(), realtime);
			}
		}
		int matched = 0, inserted = 0, updated = 0, orphan = 0;
		int zeroAvailableMatched = 0, zeroAvailableOrphan = 0;
		List<ParkingRealtime> writes = new ArrayList<>();
		for (ParkingRealtimeCandidate candidate : candidates) {
			ParkingLot lot = lotsByExternalId.get(candidate.externalId());
			if (lot == null) {
				orphan++;
				if (Integer.valueOf(0).equals(candidate.availableSpaces())) zeroAvailableOrphan++;
				continue;
			}
			matched++;
			if (Integer.valueOf(0).equals(candidate.availableSpaces())) zeroAvailableMatched++;
			ParkingRealtime realtime = existingByLotId.get(lot.getId());
			if (realtime == null) {
				realtime = new ParkingRealtime(lot, candidate.totalSpaces(), candidate.availableSpaces(),
						null, candidate.receivedAt()); inserted++;
			} else {
				realtime.updateStatus(candidate.totalSpaces(), candidate.availableSpaces(), null,
						candidate.receivedAt()); updated++;
			}
			writes.add(realtime);
		}
		realtimeRepository.saveAll(writes);
		return new ParkingRealtimeFullUpsertResult(matched, inserted, updated, orphan,
				zeroAvailableMatched, zeroAvailableOrphan);
	}
}
