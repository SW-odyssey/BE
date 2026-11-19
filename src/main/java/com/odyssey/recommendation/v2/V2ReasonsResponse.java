package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.CellReasonStatus.AVAILABLE;
import static com.odyssey.recommendation.v2.CellReasonStatus.CELL_NOT_IN_DATASET;
import static com.odyssey.recommendation.v2.CellReasonStatus.NO_RECOMMENDATION_EVIDENCE;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.odyssey.cellcondition.repository.CellConditionRepository.ReasonRow;

/**
 * 칸 번호로 직접 조회한 추천 이유. 요청한 칸은 누락되거나 기준에 못 미쳐도 키와 상태를 남긴다.
 *
 * <p>칸 번호는 내부에서 숫자로 쓰고, 응답의 JSON 객체 키가 될 때 문자열로 바뀐다. 키의 순서는 요청한
 * cellIds 순서다.
 */
public record V2ReasonsResponse(V2Context context, Map<Long, CellReason> reasonsByCellId) {

	/** 이유 코드 하나. 문장과 언어별 문구는 FE 가 만든다. */
	public record Reason(String code) {
	}

	/**
	 * 칸 하나의 상태와 이유.
	 *
	 * @param reasons 표시 순서대로 최대 2개
	 */
	public record CellReason(CellReasonStatus status, List<Reason> reasons) {

		/**
		 * 한 칸의 조회 결과로 상태와 이유를 정한다. 상태를 정하는 규칙은 여기에만 있다.
		 *
		 * <p>행이 없으면 {@code CELL_NOT_IN_DATASET} 이다. 추천에 쓸 수 있는 칸인데 이유가 하나도 없으면
		 * {@code NO_RECOMMENDATION_EVIDENCE} 이고, 나머지는 품질 상태를 그대로 쓴다.
		 *
		 * @param rows 같은 칸의 행. 이유가 없는 칸은 이유 코드가 null 인 행 하나다
		 */
		static CellReason from(List<ReasonRow> rows) {
			if (rows.isEmpty()) {
				return new CellReason(CELL_NOT_IN_DATASET, List.of());
			}
			List<Reason> reasons = rows.stream()
				.filter(row -> row.code() != null)
				.sorted(Comparator.comparing(ReasonRow::position))
				.map(row -> new Reason(row.code()))
				.toList();
			CellReasonStatus status = CellReasonStatus.of(rows.getFirst().qualityStatus());
			if (status == AVAILABLE && reasons.isEmpty()) {
				status = NO_RECOMMENDATION_EVIDENCE;
			}
			return new CellReason(status, reasons);
		}
	}
}
