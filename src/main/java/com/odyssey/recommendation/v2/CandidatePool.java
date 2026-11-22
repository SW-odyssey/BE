package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Map;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;

/**
 * 한 코스에 쓸 시간대별 후보. DB 를 모르는 순수한 값이고, 출발점마다 목록 API 와 같은 규칙으로 거른다.
 *
 * <p>후보 조회는 시간대 기준이라 출발점과 무관하다. 그래서 코스에 필요한 시간대를 미리 한 번에 읽어 두고,
 * 앞 구간에서 고른 매장 좌표가 정해질 때마다 여기서 거리만 다시 거른다.
 *
 * @param context 시작 시간대 기준의 응답 context
 * @param bySlot  시간대별 후보. 방문 백분위 하한은 조회 SQL 이 이미 적용했다
 */
record CandidatePool(V2Context context, Map<TimeSlot, List<Candidate>> bySlot) {

	CandidatePool {
		bySlot = Map.copyOf(bySlot);
	}

	/** @return 목록 API 와 같은 순서·같은 반경의 후보. 읽지 않은 시간대면 빈 목록 */
	List<Selected> near(TimeSlot slot, StoreKind kind, GeoPoint origin, double minPercentile) {
		V2RecommendationQuery query = new V2RecommendationQuery(context.datasetId(), context.weekday(), slot, origin,
			kind.category(), kind, "");
		List<Candidate> eligible = bySlot.getOrDefault(slot, List.of()).stream()
			.filter(candidate -> candidate.percentile() >= minPercentile)
			.toList();
		return V2RecommendationSelector.select(eligible, query);
	}
}
