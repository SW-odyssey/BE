package com.odyssey.cell;

import java.time.LocalTime;

public enum TimeSlot {
	LUNCH(11, 14),
	AFTERNOON(14, 18),
	DINNER(18, 22),
	LATE_NIGHT(22, 2);

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
