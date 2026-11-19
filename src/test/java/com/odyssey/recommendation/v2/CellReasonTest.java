package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.odyssey.cellcondition.entity.CellQualityStatus;
import com.odyssey.cellcondition.repository.CellConditionRepository.ReasonRow;
import com.odyssey.recommendation.v2.V2ReasonsResponse.CellReason;
import com.odyssey.recommendation.v2.V2ReasonsResponse.Reason;

/** 칸의 상태와 이유를 정하는 규칙({@link CellReason#from})을 mock 없이 확인한다. */
class CellReasonTest {

	private static ReasonRow row(CellQualityStatus quality, Integer position, String code) {
		return new ReasonRow(101, quality, position, code);
	}

	@Test
	@DisplayName("조회된 행이 없다 → CELL_NOT_IN_DATASET 과 빈 이유다")
	void cellNotInDataset() {
		// when
		CellReason reason = CellReason.from(List.of());

		// then
		assertThat(reason)
			.isEqualTo(new CellReason(CellReasonStatus.CELL_NOT_IN_DATASET, List.of()));
	}

	@Test
	@DisplayName("AVAILABLE 이고 이유가 둘이다 → 이유를 표시 순서(position)대로 준다")
	void availableWithTwoReasons() {
		// given
		List<ReasonRow> rows = List.of(
			row(CellQualityStatus.AVAILABLE, 1, "VISIT_RELATIVE_HIGH"),
			row(CellQualityStatus.AVAILABLE, 2, "TIME_PEAK"));

		// when
		CellReason reason = CellReason.from(rows);

		// then
		assertThat(reason.status()).isEqualTo(CellReasonStatus.AVAILABLE);
		assertThat(reason.reasons())
			.containsExactly(new Reason("VISIT_RELATIVE_HIGH"), new Reason("TIME_PEAK"));
	}

	@Test
	@DisplayName("이유 행이 위치 순서와 다르게 들어온다 → 입력 순서가 아니라 위치 순서로 준다")
	void ordersByPositionNotByInput() {
		// given: 위치 2 가 먼저 들어온다
		List<ReasonRow> rows = List.of(
			row(CellQualityStatus.AVAILABLE, 2, "TIME_PEAK"),
			row(CellQualityStatus.AVAILABLE, 1, "VISIT_RELATIVE_HIGH"));

		// when
		CellReason reason = CellReason.from(rows);

		// then
		assertThat(reason.reasons())
			.containsExactly(new Reason("VISIT_RELATIVE_HIGH"), new Reason("TIME_PEAK"));
	}

	@Test
	@DisplayName("AVAILABLE 인데 이유가 하나도 없다 → NO_RECOMMENDATION_EVIDENCE 와 빈 이유다")
	void availableWithoutReasons() {
		// given: 이유 없는 칸은 위치와 코드가 null 인 행 하나다
		List<ReasonRow> rows = List.of(row(CellQualityStatus.AVAILABLE, null, null));

		// when
		CellReason reason = CellReason.from(rows);

		// then
		assertThat(reason)
			.isEqualTo(new CellReason(CellReasonStatus.NO_RECOMMENDATION_EVIDENCE, List.of()));
	}

	@ParameterizedTest
	@EnumSource(value = CellQualityStatus.class, mode = EnumSource.Mode.EXCLUDE,
		names = "AVAILABLE")
	@DisplayName("AVAILABLE 이 아닌 칸 → 품질 상태를 그대로 주고 이유는 비운다")
	void nonAvailablePassesThrough(CellQualityStatus quality) {
		// given
		List<ReasonRow> rows = List.of(row(quality, null, null));

		// when
		CellReason reason = CellReason.from(rows);

		// then
		assertThat(reason.status().name()).isEqualTo(quality.name());
		assertThat(reason.reasons()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(CellQualityStatus.class)
	@DisplayName("DB 품질 상태 네 가지 → 같은 이름의 응답 상태로 바뀐다")
	void everyQualityStatusHasSameNamedReasonStatus(CellQualityStatus quality) {
		assertThat(CellReasonStatus.of(quality).name()).isEqualTo(quality.name());
	}
}
