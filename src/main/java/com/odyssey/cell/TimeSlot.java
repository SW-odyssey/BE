package com.odyssey.cell;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;

public enum TimeSlot {
	LUNCH(11, 13),
	AFTERNOON(14, 17),
	DINNER(18, 21),
	LATE_NIGHT(22, 1);

	private final int startHour;
	private final int endHourExclusive;

	TimeSlot(int startHour, int endHourExclusive) {
		this.startHour = startHour;
		this.endHourExclusive = endHourExclusive;
	}

	/** 그 시각이 속한 시간대. 시간대 사이의 시각이나 심야가 끝난 뒤의 시각이면 비어 있다. */
	public static Optional<TimeSlot> at(LocalTime time) {
		return Arrays.stream(values())
			.filter(slot -> slot.contains(time))
			.findFirst();
	}

	private boolean contains(LocalTime time) {
		boolean startedByNow = !time.isBefore(startTime());
		boolean notEndedYet = time.isBefore(endTimeExclusive());
		return endsNextDay() ? startedByNow || notEndedYet : startedByNow && notEndedYet;
	}

	public LocalTime startTime() {
		return LocalTime.of(startHour, 0);
	}

	public LocalTime endTimeExclusive() {
		return LocalTime.of(endHourExclusive, 0);
	}

	public boolean endsNextDay() {
		return endHourExclusive <= startHour;
	}
}
