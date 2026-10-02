package com.parkeasy.backend.parking.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "parking_lot")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingLot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "address", length = 500)
	private String address;

	@Column(name = "latitude", nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(name = "longitude", nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Enumerated(EnumType.STRING)
	@Column(name = "parking_type", length = 30)
	private ParkingType parkingType;

	@Column(name = "total_spaces")
	private Integer totalSpaces;

	@Column(name = "base_time")
	private Integer baseTime;

	@Column(name = "base_fee")
	private Integer baseFee;

	@Column(name = "additional_time")
	private Integer additionalTime;

	@Column(name = "additional_fee")
	private Integer additionalFee;

	@Column(name = "daily_max_fee")
	private Integer dailyMaxFee;

	@Column(name = "weekday_open_time")
	private LocalTime weekdayOpenTime;

	@Column(name = "weekday_close_time")
	private LocalTime weekdayCloseTime;

	@Column(name = "is_free", nullable = false)
	private boolean free;

	@Column(name = "is_24_hours", nullable = false)
	private boolean open24Hours;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_source", length = 50)
	private DataSource dataSource;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public ParkingLot(String name, String address, BigDecimal latitude, BigDecimal longitude,
			ParkingType parkingType, Integer totalSpaces, Integer baseTime, Integer baseFee,
			Integer additionalTime, Integer additionalFee, Integer dailyMaxFee,
			LocalTime weekdayOpenTime, LocalTime weekdayCloseTime, boolean free,
			boolean open24Hours, DataSource dataSource, String externalId) {
		this.name = name;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.parkingType = parkingType;
		this.totalSpaces = totalSpaces;
		this.baseTime = baseTime;
		this.baseFee = baseFee;
		this.additionalTime = additionalTime;
		this.additionalFee = additionalFee;
		this.dailyMaxFee = dailyMaxFee;
		this.weekdayOpenTime = weekdayOpenTime;
		this.weekdayCloseTime = weekdayCloseTime;
		this.free = free;
		this.open24Hours = open24Hours;
		this.dataSource = dataSource;
		this.externalId = externalId;
	}

	public void updateFacilityInfo(String name, String address, BigDecimal latitude,
			BigDecimal longitude, Integer totalSpaces) {
		this.name = name;
		this.address = address;
		this.latitude = latitude;
		this.longitude = longitude;
		this.totalSpaces = totalSpaces;
	}

	@PrePersist
	private void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	private void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
