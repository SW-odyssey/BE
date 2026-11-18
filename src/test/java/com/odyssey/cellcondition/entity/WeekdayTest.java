package com.odyssey.cellcondition.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.global.api.ApiEnums;

class WeekdayTest {

	@Test
	@DisplayName("요일을 선언 순서대로 API 값으로 바꾼다 → 월요일부터 일요일까지 소문자다")
	void apiValuesInDeclaredOrder() {
		// given
		Weekday[] weekdays = Weekday.values();

		// when
		List<String> values = Arrays.stream(weekdays).map(ApiEnums::toValue).toList();

		// then
		assertThat(values).containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
	}
}
