package com.odyssey.global.api;

import java.time.LocalDate;

/**
 * 방문 날짜로 고를 수 있는 범위. meta API 가 알려 주는 범위와 추천 API 가 검사하는 범위가 같도록 한 곳에 둔다.
 *
 * <p>서울 기준 오늘부터 1년 뒤까지다. 지난 날짜는 고를 수 없다.
 */
public final class VisitDateRange {

	/** 오늘부터 며칠이 아니라 달력 기준 1년 (오늘이 2026-10-31 이면 2027-10-31 까지) */
	private static final int YEARS_AHEAD = 1;

	private VisitDateRange() {
	}

	/** 고를 수 있는 가장 이른 날짜 (오늘) */
	public static LocalDate min(LocalDate today) {
		return today;
	}

	/** 고를 수 있는 가장 늦은 날짜 (오늘부터 1년 뒤) */
	public static LocalDate max(LocalDate today) {
		return today.plusYears(YEARS_AHEAD);
	}

	/** 두 끝을 포함해서 범위 안인지 */
	public static boolean contains(LocalDate today, LocalDate date) {
		return !date.isBefore(min(today)) && !date.isAfter(max(today));
	}
}
