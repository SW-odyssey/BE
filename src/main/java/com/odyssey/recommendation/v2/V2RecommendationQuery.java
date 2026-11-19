package com.odyssey.recommendation.v2;

import java.util.Objects;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

/**
 * 추천 목록 요청. 값을 enum 으로 바꾸는 일은 컨트롤러가 하고, 여기서는 항상 지켜야 할 조건만 확인한다.
 *
 * <p>좌표 범위는 {@link GeoPoint} 가 확인한다. 반경은 도보거리가 아닌 직선거리다.
 *
 * @param datasetId 분석 배치 ID. meta 가 준 값
 * @param origin    사용자 위치
 * @param category  대분류. null 이면 조건 없음(all)
 * @param kind      가게 분류. null 이면 조건 없음(all)
 * @param q         이름 부분 검색어. 앞뒤 공백을 뗀 80자 이하이고, 없으면 빈 문자열
 */
public record V2RecommendationQuery(String datasetId, Weekday weekday, TimeSlot slot,
	GeoPoint origin, Category category, StoreKind kind, String q) {

	static final int MAX_QUERY_LENGTH = 80;

	public V2RecommendationQuery {
		Objects.requireNonNull(weekday, "weekday");
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(origin, "origin");
		if (datasetId == null || !datasetId.matches("[a-zA-Z0-9_-]{1,80}")) {
			throw invalid("meta에서 제공한 datasetId가 필요합니다.");
		}
		q = q == null ? "" : q.strip();
		if (q.length() > MAX_QUERY_LENGTH) {
			throw invalid("q는 80자까지입니다.");
		}
		if (category != null && kind != null && kind.category() != category) {
			throw invalid("category와 kind가 서로 맞지 않습니다.");
		}
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
	}
}
