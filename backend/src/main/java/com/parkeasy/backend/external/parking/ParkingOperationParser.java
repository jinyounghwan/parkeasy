package com.parkeasy.backend.external.parking;

import java.time.DateTimeException;
import java.time.LocalTime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.parkeasy.backend.parking.domain.ParkingOperationDetails;

@Component
public class ParkingOperationParser {

	public ParkingOperationDetails parse(PublicParkingOperationItem item) {
		TimeRange monday = parseRange(item.monday()); TimeRange tuesday = parseRange(item.tuesday());
		TimeRange wednesday = parseRange(item.wednesday()); TimeRange thursday = parseRange(item.thursday());
		TimeRange friday = parseRange(item.friday()); TimeRange saturday = parseRange(item.saturday());
		TimeRange sunday = parseRange(item.sunday()); TimeRange holiday = parseRange(item.holiday());
		PublicParkingBasicInfo basic = item.basicInfo();
		PublicParkingFixedAmountInfo fixed = item.fixedAmountInfo();
		return new ParkingOperationDetails(
				parseInteger(item.baseFreeTime()),
				parseInteger(basic == null ? null : basic.baseTime()),
				parseInteger(basic == null ? null : basic.baseFee()),
				parseInteger(basic == null ? null : basic.additionalTime()),
				parseInteger(basic == null ? null : basic.additionalFee()),
				parseInteger(fixed == null ? null : fixed.dailyMaxFee()),
				parseInteger(fixed == null ? null : fixed.monthlyFee()),
				monday.open(), monday.close(), tuesday.open(), tuesday.close(),
				wednesday.open(), wednesday.close(), thursday.open(), thursday.close(),
				friday.open(), friday.close(), saturday.open(), saturday.close(),
				sunday.open(), sunday.close(), holiday.open(), holiday.close());
	}

	public boolean hasAnyValue(ParkingOperationDetails details) {
		return details.baseFreeTime() != null || details.baseTime() != null || details.baseFee() != null
				|| details.additionalTime() != null || details.additionalFee() != null
				|| details.dailyMaxFee() != null || details.monthlyFee() != null
				|| details.mondayOpenTime() != null || details.mondayCloseTime() != null
				|| details.tuesdayOpenTime() != null || details.tuesdayCloseTime() != null
				|| details.wednesdayOpenTime() != null || details.wednesdayCloseTime() != null
				|| details.thursdayOpenTime() != null || details.thursdayCloseTime() != null
				|| details.fridayOpenTime() != null || details.fridayCloseTime() != null
				|| details.saturdayOpenTime() != null || details.saturdayCloseTime() != null
				|| details.sundayOpenTime() != null || details.sundayCloseTime() != null
				|| details.holidayOpenTime() != null || details.holidayCloseTime() != null;
	}

	Integer parseInteger(String value) {
		try { return StringUtils.hasText(value) ? Integer.valueOf(value.trim()) : null; }
		catch (NumberFormatException exception) { return null; }
	}

	TimeRange parseRange(PublicParkingOperationTime value) {
		if (value == null) return TimeRange.EMPTY;
		if ("000000".equals(trim(value.startTime())) && "000000".equals(trim(value.endTime()))) {
			return TimeRange.EMPTY;
		}
		return new TimeRange(parseTime(value.startTime()), parseTime(value.endTime()));
	}

	private LocalTime parseTime(String value) {
		String time = trim(value);
		if (time == null || !time.matches("\\d{6}")) return null;
		try { return LocalTime.of(Integer.parseInt(time.substring(0, 2)),
				Integer.parseInt(time.substring(2, 4)), Integer.parseInt(time.substring(4, 6))); }
		catch (DateTimeException exception) { return null; }
	}

	private String trim(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

	record TimeRange(LocalTime open, LocalTime close) {
		private static final TimeRange EMPTY = new TimeRange(null, null);
	}
}
