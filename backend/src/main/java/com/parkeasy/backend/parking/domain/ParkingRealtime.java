package com.parkeasy.backend.parking.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "parking_realtime")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingRealtime {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parking_lot_id", nullable = false, unique = true)
	private ParkingLot parkingLot;
	private Integer totalSpaces;
	private Integer availableSpaces;
	private LocalDateTime observedAt;
	@Column(nullable = false)
	private LocalDateTime receivedAt;
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public ParkingRealtime(ParkingLot parkingLot, Integer totalSpaces, Integer availableSpaces,
			LocalDateTime observedAt, LocalDateTime receivedAt) {
		this.parkingLot = parkingLot;
		updateStatus(totalSpaces, availableSpaces, observedAt, receivedAt);
	}

	public void updateStatus(Integer totalSpaces, Integer availableSpaces,
			LocalDateTime observedAt, LocalDateTime receivedAt) {
		this.totalSpaces = totalSpaces;
		this.availableSpaces = availableSpaces;
		this.observedAt = observedAt;
		this.receivedAt = receivedAt;
	}

	@PrePersist private void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
	@PreUpdate private void onUpdate() { updatedAt = LocalDateTime.now(); }
}
