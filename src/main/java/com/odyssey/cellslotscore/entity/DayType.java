package com.odyssey.cellslotscore.entity;

import java.time.LocalDate;

/** 공휴일도 해당 요일로 분류한다. */
public enum DayType {
	MON_THU, FRI, SAT, SUN;

	public static DayType from(LocalDate date) {
		return switch (date.getDayOfWeek()) {
			case FRIDAY -> FRI;
			case SATURDAY -> SAT;
			case SUNDAY -> SUN;
			default -> MON_THU;
		};
	}
}
