package com.odyssey.cellslotscore.entity;

import java.time.LocalDate;

/** 공휴일도 해당 요일로 분류한다. */
public enum DayType {
	MON_THU, FRI, SAT, SUN;

	/** 방문일을 점수 표의 월~목 / 금 / 토 / 일 구분으로 바꾼다. 공휴일 예외는 두지 않는다. */
	public static DayType from(LocalDate date) {
		return switch (date.getDayOfWeek()) {
			case FRIDAY -> FRI;
			case SATURDAY -> SAT;
			case SUNDAY -> SUN;
			default -> MON_THU;
		};
	}
}
