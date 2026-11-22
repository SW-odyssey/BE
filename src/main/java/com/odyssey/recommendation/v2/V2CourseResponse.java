package com.odyssey.recommendation.v2;

import java.util.List;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;

/**
 * context.slot은 시작 시간대이고, 각 매장의 직선거리는 직전 출발 위치가 기준이다.
 *
 * @param seed 후보를 섞은 값. 다시 보내면 같은 순서로 확인한다
 */
public record V2CourseResponse(String status, V2Context context, long seed,
	List<Stop> stops, Interruption interruption) {

	static V2CourseResponse complete(V2Context context, long seed, List<Stop> stops) {
		return new V2CourseResponse("COMPLETE", context, seed, List.copyOf(stops), null);
	}

	static V2CourseResponse interrupted(V2Context context, long seed, List<Stop> stops, Interruption interruption) {
		String status = stops.isEmpty() ? "NO_MATCHES" : "PARTIAL";
		return new V2CourseResponse(status, context, seed, List.copyOf(stops), interruption);
	}

	/**
	 * @param candidateRank 제외·무작위 선택 전 목록 API 의 순번
	 * @param hoursUnknown  영업 중으로 등록돼 있지만 정규 영업시간이 없다. 쇼핑에서만 true 가 될 수 있다
	 */
	public record Stop(TimeSlot slot, String visitTime, int candidateRank, Item place, boolean hoursUnknown) {
	}

	/** 이어갈 곳이 없는 첫 구간. 매장 부족, 영업 근거 부족, 외부 조회 실패를 구분한다. */
	public record Interruption(TimeSlot slot, String reason) {
	}
}
