package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** {@link V2CellReasonsQuery} 의 칸 번호 검증을 확인한다. */
class V2CellReasonsQueryTest {

	private static V2CellReasonsQuery query(List<Long> cellIds) {
		return new V2CellReasonsQuery("nov", Weekday.SUN, TimeSlot.DINNER, cellIds);
	}

	static Stream<List<Long>> invalidCellIds() {
		return Stream.of(
			List.of(),
			List.of(1L, 1L),
			List.of(0L),
			List.of(-1L),
			Arrays.asList(1L, null),
			LongStream.rangeClosed(1, 31).boxed().toList());
	}

	@ParameterizedTest
	@MethodSource("invalidCellIds")
	@DisplayName("빈 목록 · 중복 · 0 · 음수 · null · 31개인 칸 목록 → INVALID_INPUT")
	void rejectsInvalidCellIds(List<Long> cellIds) {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> query(cellIds));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception.getMessage()).isEqualTo("cellIds는 중복 없는 양의 정수 1~30개입니다.");
	}

	@Test
	@DisplayName("칸 번호 목록이 null 이다 → INVALID_INPUT")
	void rejectsNullCellIds() {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> query(null));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("칸이 30개다 → 모두 받는다")
	void acceptsThirtyCells() {
		// given
		List<Long> cellIds = LongStream.rangeClosed(1, 30).boxed().toList();

		// when
		V2CellReasonsQuery query = query(cellIds);

		// then
		assertThat(query.cellIds()).hasSize(30);
	}

	@Test
	@DisplayName("칸 번호를 뒤섞어 요청한다 → 요청한 순서를 그대로 유지한다")
	void keepsRequestOrder() {
		// when
		V2CellReasonsQuery query = query(List.of(3L, 1L, 2L));

		// then
		assertThat(query.cellIds()).containsExactly(3L, 1L, 2L);
	}

	@Test
	@DisplayName("요청 뒤에 원래 목록을 바꾼다 → 요청 안의 칸 번호는 바뀌지 않는다")
	void copiesCellIds() {
		// given
		List<Long> cellIds = new ArrayList<>(List.of(1L, 2L));
		V2CellReasonsQuery query = query(cellIds);

		// when
		cellIds.add(3L);

		// then
		assertThat(query.cellIds()).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("datasetId 형식이 잘못됐다 → INVALID_INPUT")
	void rejectsInvalidDatasetId() {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> new V2CellReasonsQuery("a b", Weekday.SUN, TimeSlot.DINNER, List.of(1L)));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}
}
