package com.parkeasy.backend.parking.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

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
@Table(name = "parking_operation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingOperation {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parking_lot_id", nullable = false, unique = true)
	private ParkingLot parkingLot;

	private Integer baseFreeTime;
	private Integer baseTime;
	private Integer baseFee;
	private Integer additionalTime;
	private Integer additionalFee;
	private Integer dailyMaxFee;
	private Integer monthlyFee;
	private LocalTime mondayOpenTime;
	private LocalTime mondayCloseTime;
	private LocalTime tuesdayOpenTime;
	private LocalTime tuesdayCloseTime;
	private LocalTime wednesdayOpenTime;
	private LocalTime wednesdayCloseTime;
	private LocalTime thursdayOpenTime;
	private LocalTime thursdayCloseTime;
	private LocalTime fridayOpenTime;
	private LocalTime fridayCloseTime;
	private LocalTime saturdayOpenTime;
	private LocalTime saturdayCloseTime;
	private LocalTime sundayOpenTime;
	private LocalTime sundayCloseTime;
	private LocalTime holidayOpenTime;
	private LocalTime holidayCloseTime;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	public ParkingOperation(ParkingLot parkingLot, ParkingOperationDetails details) {
		this.parkingLot = parkingLot;
		updateOperationInfo(details);
	}

	public void updateOperationInfo(ParkingOperationDetails details) {
		baseFreeTime = details.baseFreeTime(); baseTime = details.baseTime(); baseFee = details.baseFee();
		additionalTime = details.additionalTime(); additionalFee = details.additionalFee();
		dailyMaxFee = details.dailyMaxFee(); monthlyFee = details.monthlyFee();
		mondayOpenTime = details.mondayOpenTime(); mondayCloseTime = details.mondayCloseTime();
		tuesdayOpenTime = details.tuesdayOpenTime(); tuesdayCloseTime = details.tuesdayCloseTime();
		wednesdayOpenTime = details.wednesdayOpenTime(); wednesdayCloseTime = details.wednesdayCloseTime();
		thursdayOpenTime = details.thursdayOpenTime(); thursdayCloseTime = details.thursdayCloseTime();
		fridayOpenTime = details.fridayOpenTime(); fridayCloseTime = details.fridayCloseTime();
		saturdayOpenTime = details.saturdayOpenTime(); saturdayCloseTime = details.saturdayCloseTime();
		sundayOpenTime = details.sundayOpenTime(); sundayCloseTime = details.sundayCloseTime();
		holidayOpenTime = details.holidayOpenTime(); holidayCloseTime = details.holidayCloseTime();
	}

	@PrePersist
	private void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
	@PreUpdate
	private void onUpdate() { updatedAt = LocalDateTime.now(); }
}
