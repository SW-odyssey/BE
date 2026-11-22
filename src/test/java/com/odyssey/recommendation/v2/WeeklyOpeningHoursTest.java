package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.GooglePlaceHours.Period;
import com.odyssey.recommendation.v2.GooglePlaceHours.Point;

/** {@link GooglePlaceHours} 의 주 단위 영업시간 계산을 확인한다. */
class WeeklyOpeningHoursTest {

	private static final int SUN = 0, FRI = 5, SAT = 6;
	/** 금요일 11~15시 */
	private static final List<Period> FRI_LUNCH = List.of(period(FRI, 11, FRI, 15));
	/** 금 18시~토 2시, 토 18시~일 2시 */
	private static final List<Period> OVERNIGHT = List.of(period(FRI, 18, SAT, 2), period(SAT, 18, SUN, 2));
	/** 토 18시~자정, 금 18시~토 1시 */
	private static final List<Period> BAR = List.of(period(SAT, 18, SUN, 0), period(FRI, 18, SAT, 1));

	private static Period period(int openDay, int openHour, int closeDay, int closeHour) {
		return new Period(new Point(openDay, openHour, 0), new Point(closeDay, closeHour, 0));
	}

	@ParameterizedTest(name = "{0} {1} 도착 → {2}")
	@CsvSource({"FRI, 12:30, true", "FRI, 11:00, true", "FRI, 15:00, false", "FRI, 10:59, false",
		"THU, 12:30, false"})
	@DisplayName("머무는 시간 없이 도착 → 영업 구간 안이면 영업, 마감 정각과 다른 요일은 아니다")
	void arrivalWithinPeriod(Weekday weekday, LocalTime arrival, boolean expected) {
		// when
		boolean open = GooglePlaceHours.coversStay(FRI_LUNCH, weekday, arrival, 0);

		// then
		assertThat(open).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} {1} 도착 → {2}")
	@CsvSource({"FRI, 23:30, true", "SAT, 01:00, true", "SUN, 01:00, true", "SUN, 23:30, false"})
	@DisplayName("자정이나 토→일을 넘는 구간에 도착 → 넘어간 뒤에도 같은 구간으로 본다")
	void periodCrossingMidnightOrWeek(Weekday weekday, LocalTime arrival, boolean expected) {
		// when
		boolean open = GooglePlaceHours.coversStay(OVERNIGHT, weekday, arrival, 0);

		// then
		assertThat(open).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} {1} 도착 {2}분 → {3}")
	@CsvSource({"FRI, 14:10, 50, true", "FRI, 14:20, 50, false", "FRI, 14:00, 60, true",
		"FRI, 14:01, 60, false"})
	@DisplayName("머무는 중에 마감 → 영업이 아니고, 마감 정각에 나오면 영업이다")
	void stayUntilClose(Weekday weekday, LocalTime arrival, int stayMinutes, boolean expected) {
		// when
		boolean open = GooglePlaceHours.coversStay(FRI_LUNCH, weekday, arrival, stayMinutes);

		// then
		assertThat(open).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} {1} 도착 {2}분 → {3}")
	@CsvSource({"SAT, 23:30, 70, false", "FRI, 23:30, 70, true"})
	@DisplayName("머무는 시간이 자정을 넘음 → 그 구간의 마감까지 이어지는지로 본다")
	void stayCrossingMidnight(Weekday weekday, LocalTime arrival, int stayMinutes, boolean expected) {
		// when
		boolean open = GooglePlaceHours.coversStay(BAR, weekday, arrival, stayMinutes);

		// then
		assertThat(open).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} {1} 도착 → 영업")
	@CsvSource({"WED, 23:30", "SUN, 00:00", "SAT, 23:59"})
	@DisplayName("마감이 없는 구간 → 24시간 영업이다")
	void noCloseMeansAlwaysOpen(Weekday weekday, LocalTime arrival) {
		// given
		List<Period> always = List.of(new Period(new Point(SUN, 0, 0), null));

		// when
		boolean open = GooglePlaceHours.coversStay(always, weekday, arrival, 70);

		// then
		assertThat(open).isTrue();
	}

	@Test
	@DisplayName("영업 구간이 없음 → 영업이 아니다")
	void nullPeriods() {
		// when
		boolean open = GooglePlaceHours.coversStay(null, Weekday.WED, LocalTime.of(12, 30), 0);

		// then
		assertThat(open).isFalse();
	}
}
