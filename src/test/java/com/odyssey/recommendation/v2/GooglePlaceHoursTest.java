package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;

/** {@link GooglePlaceHours} 가 Google 응답을 {@link Result} 로 바꾸는지 확인한다. 시각 계산은 WeeklyOpeningHoursTest. */
class GooglePlaceHoursTest {

	@Test
	@DisplayName("API 키가 없음 → 외부를 부르지 않고 확인 불가다")
	void noApiKey() {
		// given
		var hours = new GooglePlaceHours("", "http://127.0.0.1:9");

		// when
		Result result = hours.check("place", Weekday.FRI, LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.UNAVAILABLE);
	}
}
