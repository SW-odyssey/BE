package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.cellinsight.repository.CellInsightRepository.ReasonRow;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendation.v2.V2ReasonsResponse.CellReason;

/**
 * {@link V2CellReasonService} 를 확인한다. 리포지토리는 목으로 대신한다.
 *
 * <p>칸마다 키와 상태를 항상 돌려주는 것이 계약이다. 누락 · 기준 미달 칸도 응답에서 사라지면 안 된다.
 */
class V2CellReasonServiceTest {

	private static final String DS = "nov";

	private CellInsightRepository repo;
	private V2CellReasonService service;

	@BeforeEach
	void setup() {
		repo = mock(CellInsightRepository.class);
		when(repo.datasets()).thenReturn(List.of(new Dataset(DS, "2025-11", "DOMESTIC_20_39",
			"visit75-band300-v2-split", "visit75-no-worker-v2-split",
			"visit-only-relative100-step5-v2-split")));
		service = new V2CellReasonService(repo, new V2DatasetResolver(repo));
	}

	private void rows(List<Long> ids, ReasonRow... rows) {
		when(repo.reasons(DS, "mon", "dinner", ids)).thenReturn(List.of(rows));
	}

	private V2ReasonsResponse call(List<Long> ids) {
		return service.reasons(DS, "mon", "dinner", ids);
	}

	private static void assertInvalid(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT));
	}

	@Test
	@DisplayName("이유가 둘인 칸 → 상태 AVAILABLE 과 이유 코드 둘을 받은 순서대로 준다")
	void availableWithReasons() {
		// given
		rows(List.of(1L),
			new ReasonRow("1", "AVAILABLE", 1, "VISIT_RELATIVE_HIGH"),
			new ReasonRow("1", "AVAILABLE", 2, "TIME_PEAK"));

		// when
		V2ReasonsResponse response = call(List.of(1L));

		// then
		CellReason cell = response.reasonsByCellId().get("1");
		assertThat(cell.status()).isEqualTo("AVAILABLE");
		assertThat(cell.reasons()).extracting(V2ReasonsResponse.Reason::code)
			.containsExactly("VISIT_RELATIVE_HIGH", "TIME_PEAK");
		assertThat(response.context()).isEqualTo(new V2Context(DS, "2025-11", "mon", "dinner"));
	}

	@Test
	@DisplayName("칸 번호를 거꾸로 요청한다 → 요청한 순서대로 키를 준다")
	void keepsRequestOrder() {
		// given: 리포지토리는 칸 번호순으로 돌려준다
		rows(List.of(3L, 1L, 2L),
			new ReasonRow("1", "AVAILABLE", 1, "DAY_DISTINCT"),
			new ReasonRow("2", "AVAILABLE", 1, "STAY_DISTINCT"),
			new ReasonRow("3", "AVAILABLE", 1, "WALK_DISTINCT"));

		// when
		V2ReasonsResponse response = call(List.of(3L, 1L, 2L));

		// then
		assertThat(response.reasonsByCellId().keySet()).containsExactly("3", "1", "2");
	}

	@Test
	@DisplayName("데이터셋에 없는 칸 → CELL_NOT_IN_DATASET 과 빈 이유로 키를 남긴다")
	void unknownCellKeepsKey() {
		// given
		rows(List.of(99L));

		// when
		V2ReasonsResponse response = call(List.of(99L));

		// then
		assertThat(response.reasonsByCellId().get("99"))
			.isEqualTo(new CellReason("CELL_NOT_IN_DATASET", List.of()));
	}

	@Test
	@DisplayName("AVAILABLE 인데 보여줄 이유가 없다(작업자 이유뿐) → NO_RECOMMENDATION_EVIDENCE")
	void availableWithOnlyHiddenReason() {
		// given: WORKER_DISTINCT 는 응답에 내지 않는다
		rows(List.of(1L), new ReasonRow("1", "AVAILABLE", 1, "WORKER_DISTINCT"));

		// when
		V2ReasonsResponse response = call(List.of(1L));

		// then
		assertThat(response.reasonsByCellId().get("1"))
			.isEqualTo(new CellReason("NO_RECOMMENDATION_EVIDENCE", List.of()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"NOT_OBSERVED", "INCOMPLETE", "NO_POSITIVE_VISITS"})
	@DisplayName("AVAILABLE 이 아닌 칸 → 품질 상태를 그대로 주고 이유는 비운다")
	void nonAvailableStatusPassesThrough(String status) {
		// given
		rows(List.of(1L), new ReasonRow("1", status, null, null));

		// when
		V2ReasonsResponse response = call(List.of(1L));

		// then
		assertThat(response.reasonsByCellId().get("1")).isEqualTo(new CellReason(status, List.of()));
	}

	@Test
	@DisplayName("칸 30개 → 통과하고, 31개 → INVALID_INPUT")
	void cellCountLimit() {
		// given
		List<Long> thirty = LongStream.rangeClosed(1, 30).boxed().toList();
		List<Long> thirtyOne = LongStream.rangeClosed(1, 31).boxed().toList();
		when(repo.reasons(DS, "mon", "dinner", thirty)).thenReturn(List.of());

		// when / then
		assertThatCode(() -> call(thirty)).doesNotThrowAnyException();
		assertInvalid(() -> call(thirtyOne));
	}

	@Test
	@DisplayName("빈 목록 · 중복 · 0 · 음수 · null 이 든 칸 목록 → INVALID_INPUT, DB 는 조회하지 않는다")
	void invalidCellIds() {
		// when / then
		assertInvalid(() -> call(List.of()));
		assertInvalid(() -> call(List.of(1L, 1L)));
		assertInvalid(() -> call(List.of(0L)));
		assertInvalid(() -> call(List.of(-1L)));
		assertInvalid(() -> call(Arrays.asList(1L, null)));
		verify(repo, never()).reasons(anyString(), anyString(), anyString(), anyList());
	}

	@Test
	@DisplayName("허용하지 않는 요일 · 시간대 → INVALID_INPUT, DB 는 조회하지 않는다")
	void invalidWeekdayAndSlot() {
		// when / then
		assertInvalid(() -> service.reasons(DS, "mon_thu", "dinner", List.of(1L)));
		assertInvalid(() -> service.reasons(DS, "mon", "evening", List.of(1L)));
		verify(repo, never()).reasons(anyString(), anyString(), anyString(), anyList());
	}
}
