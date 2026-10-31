package com.odyssey.global.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VisitDateRangeTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

	@Test
	@DisplayName("오늘과 1년 뒤 같은 날은 범위 안이다")
	void bothEndsInclusive() {
		// given: 2026-10-31

		// when
		boolean today = VisitDateRange.contains(TODAY, TODAY);
		boolean lastDay = VisitDateRange.contains(TODAY, LocalDate.of(2027, 10, 31));

		// then
		assertThat(today).isTrue();
		assertThat(lastDay).isTrue();
	}

	@Test
	@DisplayName("어제와 1년하고 하루 뒤는 범위 밖이다")
	void outside() {
		// given: 2026-10-31

		// when
		boolean yesterday = VisitDateRange.contains(TODAY, LocalDate.of(2026, 10, 30));
		boolean tooFar = VisitDateRange.contains(TODAY, LocalDate.of(2027, 11, 1));

		// then
		assertThat(yesterday).isFalse();
		assertThat(tooFar).isFalse();
	}

	@Test
	@DisplayName("오늘이 윤일이면 1년 뒤는 다음 해 2월 28일이다")
	void leapDay() {
		// given: 2028-02-29

		// when
		LocalDate max = VisitDateRange.max(LocalDate.of(2028, 2, 29));

		// then
		assertThat(max).isEqualTo(LocalDate.of(2029, 2, 28));
	}
}
