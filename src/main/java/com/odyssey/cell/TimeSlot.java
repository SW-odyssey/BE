package com.odyssey.cell;

import java.time.LocalTime;

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
