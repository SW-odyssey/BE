package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Objects;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * 추천 이유 요청. 칸 번호 개수와 형식은 여기서 확인하므로, 잘못된 요청은 서비스와 DB 에 닿기 전에 400
 * 이 된다.
 *
 * @param cellIds 중복 없는 양의 정수 1~30개. 요청한 순서를 유지한다
 */
public record V2CellReasonsQuery(String datasetId, Weekday weekday, TimeSlot slot,
	List<Long> cellIds) {

	static final int MAX_CELLS = 30;

	public V2CellReasonsQuery {
		Objects.requireNonNull(weekday, "weekday");
		Objects.requireNonNull(slot, "slot");
		V2RequestParams.requireDatasetId(datasetId);
		if (cellIds == null || cellIds.isEmpty() || cellIds.size() > MAX_CELLS
			|| cellIds.stream().anyMatch(id -> id == null || id <= 0)
			|| cellIds.stream().distinct().count() != cellIds.size()) {
			throw new BusinessException(
				CommonErrorCode.INVALID_INPUT, "cellIds는 중복 없는 양의 정수 1~30개입니다.");
		}
		cellIds = List.copyOf(cellIds);
	}
}
