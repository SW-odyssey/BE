package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** {@link V2VisitPatternQuery} 의 칸 번호와 datasetId 검증을 확인한다. */
class V2VisitPatternQueryTest {

	@Test
	@DisplayName("올바른 요청 → 받은 값을 그대로 가진다")
	void valid() {
		// when
		V2VisitPatternQuery query = new V2VisitPatternQuery("nov", Weekday.SUN, 81591938L);

		// then
		assertThat(query.datasetId()).isEqualTo("nov");
		assertThat(query.weekday()).isEqualTo(Weekday.SUN);
		assertThat(query.cellId()).isEqualTo(81591938L);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("칸 번호가 0 이하 → INVALID_INPUT")
	void invalidCellId(long cellId) {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> new V2VisitPatternQuery("nov", Weekday.SUN, cellId));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception).hasMessage("cellId는 양의 정수입니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "a b", "한글"})
	@DisplayName("datasetId 형식이 틀리다 → INVALID_INPUT")
	void invalidDatasetId(String datasetId) {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> new V2VisitPatternQuery(datasetId, Weekday.SUN, 1L));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}
}
