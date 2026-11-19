package com.odyssey.recommendation.v2;

import com.fasterxml.jackson.annotation.JsonValue;
import com.odyssey.cellcondition.entity.CellQualityStatus;

/**
 * 칸의 추천 이유 상태. DB 품질 상태 네 가지에 API 가 판정하는 두 가지를 더한 여섯 가지다.
 *
 * <p>다른 enum 과 달리 응답에는 대문자 이름 그대로 나간다(계약이 AVAILABLE 형태다).
 */
public enum CellReasonStatus {
	AVAILABLE,
	NOT_OBSERVED,
	INCOMPLETE,
	NO_POSITIVE_VISITS,
	/** 추천에 쓸 수 있는 칸인데 보여 줄 이유 코드가 하나도 없다. */
	NO_RECOMMENDATION_EVIDENCE,
	/** 요청한 분석 배치·요일·시간대에 이 칸의 조건이 없다. */
	CELL_NOT_IN_DATASET;

	static CellReasonStatus of(CellQualityStatus quality) {
		return switch (quality) {
			case AVAILABLE -> AVAILABLE;
			case NOT_OBSERVED -> NOT_OBSERVED;
			case INCOMPLETE -> INCOMPLETE;
			case NO_POSITIVE_VISITS -> NO_POSITIVE_VISITS;
		};
	}

	@JsonValue
	public String value() {
		return name();
	}
}
