package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.CellQualityStatus;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.ReasonRow;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2ReasonsResponse.CellReason;
import com.odyssey.recommendation.v2.V2ReasonsResponse.Reason;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * {@link V2CellReasonService} 를 확인한다. 상태 판정 규칙은 CellReason 테스트가 보므로 여기서는 요청한
 * 칸마다 키가 남는지, 순서와 조회 조건을 지키는지만 본다.
 */
class V2CellReasonServiceTest {

	private static final String DS = "nov";
	private static final Dataset DATASET =
		new Dataset(DS, "2025-11", "DOMESTIC_20_39", "policy", "reason", "profile");

	private CellConditionRepository repository;
	private V2DatasetResolver datasets;
	private V2CellReasonService service;

	@BeforeEach
	void setUp() {
		repository = mock(CellConditionRepository.class);
		datasets = mock(V2DatasetResolver.class);
		service = new V2CellReasonService(repository, datasets);
		given(datasets.require(DS)).willReturn(DATASET);
	}

	private static V2CellReasonsQuery query(List<Long> cellIds) {
		return new V2CellReasonsQuery(DS, Weekday.SUN, TimeSlot.DINNER, cellIds);
	}

	private void givenRows(ReasonRow... rows) {
		given(repository.reasons(anyString(), any(Weekday.class), any(TimeSlot.class), anyList()))
			.willReturn(List.of(rows));
	}

	private static ReasonRow row(long cellId, int position, String code) {
		return new ReasonRow(cellId, CellQualityStatus.AVAILABLE, position, code);
	}

	@Test
	@DisplayName("이유가 둘인 칸 → AVAILABLE 과 이유 코드 둘을 표시 순서대로 준다")
	void availableWithReasons() {
		// given
		givenRows(row(81591938L, 1, "VISIT_RELATIVE_HIGH"), row(81591938L, 2, "TIME_PEAK"));

		// when
		V2ReasonsResponse response = service.reasons(query(List.of(81591938L)));

		// then
		assertThat(response.reasonsByCellId().get(81591938L)).isEqualTo(new CellReason(
			CellReasonStatus.AVAILABLE,
			List.of(new Reason("VISIT_RELATIVE_HIGH"), new Reason("TIME_PEAK"))));
	}

	@Test
	@DisplayName("조회하면 → 분석 배치의 ID 와 기준월, 요청한 요일 · 시간대를 context 로 돌려준다")
	void echoesContext() {
		// given
		givenRows();

		// when
		V2ReasonsResponse response = service.reasons(query(List.of(1L)));

		// then
		assertThat(response.context())
			.isEqualTo(new V2Context(DS, "2025-11", Weekday.SUN, TimeSlot.DINNER));
	}

	@Test
	@DisplayName("조회한다 → 요청한 배치 · 요일 · 시간대 · 칸 번호를 그대로 넘긴다")
	void passesConditionToRepository() {
		// given
		givenRows();

		// when
		service.reasons(query(List.of(3L, 1L)));

		// then
		then(repository).should().reasons(DS, Weekday.SUN, TimeSlot.DINNER, List.of(3L, 1L));
	}

	@Test
	@DisplayName("칸 번호를 거꾸로 요청한다 → 조회 결과 순서와 상관없이 요청한 순서대로 키를 준다")
	void keepsRequestOrder() {
		// given: 조회는 칸 번호 오름차순으로 온다
		givenRows(row(101L, 1, "DAY_DISTINCT"), row(103L, 1, "WALK_DISTINCT"));

		// when
		V2ReasonsResponse response = service.reasons(query(List.of(103L, 101L)));

		// then
		assertThat(response.reasonsByCellId().keySet()).containsExactly(103L, 101L);
	}

	@Test
	@DisplayName("서로 다른 두 칸의 이유 → 각자 자기 칸의 코드만 가진다")
	void reasonsStayWithTheirOwnCell() {
		// given
		givenRows(row(101L, 1, "VISIT_RELATIVE_HIGH"), row(101L, 2, "TIME_PEAK"),
			row(102L, 1, "DAY_DISTINCT"));

		// when
		V2ReasonsResponse response = service.reasons(query(List.of(101L, 102L)));

		// then
		assertThat(response.reasonsByCellId().get(101L).reasons())
			.containsExactly(new Reason("VISIT_RELATIVE_HIGH"), new Reason("TIME_PEAK"));
		assertThat(response.reasonsByCellId().get(102L).reasons())
			.containsExactly(new Reason("DAY_DISTINCT"));
	}

	@Test
	@DisplayName("조회에 없는 칸 → CELL_NOT_IN_DATASET 과 빈 이유로 키를 남긴다")
	void unknownCellKeepsKey() {
		// given
		givenRows(row(101L, 1, "DAY_DISTINCT"));

		// when
		V2ReasonsResponse response = service.reasons(query(List.of(101L, 999L)));

		// then
		assertThat(response.reasonsByCellId().get(999L))
			.isEqualTo(new CellReason(CellReasonStatus.CELL_NOT_IN_DATASET, List.of()));
	}

	@Test
	@DisplayName("칸을 30개 요청한다 → 30개 모두 키로 준다")
	void thirtyCellsAccepted() {
		// given
		givenRows();
		List<Long> ids = LongStream.rangeClosed(1, 30).boxed().toList();

		// when
		V2ReasonsResponse response = service.reasons(query(ids));

		// then
		assertThat(response.reasonsByCellId().keySet()).containsExactlyElementsOf(ids);
	}

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND 이고 DB 는 조회하지 않는다")
	void unknownDatasetSkipsQuery() {
		// given
		given(datasets.require(DS)).willThrow(new BusinessException(
			V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.reasons(query(List.of(1L))));

		// then
		assertThat(exception.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
		then(repository).should(never()).reasons(anyString(), any(Weekday.class),
			any(TimeSlot.class), anyList());
	}
}
