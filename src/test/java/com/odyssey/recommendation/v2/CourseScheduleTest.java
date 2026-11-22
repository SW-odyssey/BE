package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.recommendation.v2.CourseSchedule.Requirement;

class CourseScheduleTest {

	@ParameterizedTest
	@CsvSource({"REQUIRED, false", "OPTIONAL, true", "OPT_IN, true"})
	@DisplayName("자리를 채우지 못함 → 선택 자리는 건너뛰고 필수 자리는 중단한다")
	void skipWhenMissing(Requirement requirement, boolean expected) {
		// when
		boolean skip = requirement.skipWhenMissing();

		// then
		assertThat(skip).isEqualTo(expected);
	}
}
