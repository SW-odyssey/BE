package com.odyssey.recommendation.v2;

import java.util.Objects;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * 방문 패턴 요청. 시간대는 키에 없다. 목록에서 고른 시간대와 상관없이 같은 칸·같은 요일의 프로필을
 * 준다.
 */
public record V2VisitPatternQuery(String datasetId, Weekday weekday, long cellId) {

	public V2VisitPatternQuery {
		Objects.requireNonNull(weekday, "weekday");
		V2RequestParams.requireDatasetId(datasetId);
		if (cellId <= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "cellId는 양의 정수입니다.");
		}
	}
}
