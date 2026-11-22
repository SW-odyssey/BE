package com.odyssey.recommendation.v2;

import java.time.LocalTime;
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
	 * @param leaveTime     도착 시각에 머무는 시간을 더한 시각. 자정을 넘기면 00:00 부터 다시 센다
	 * @param walkMinutes   직전 출발 위치에서 걸어오는 분. 직선거리를 그대로 쓰므로 실제 경로와 다를 수 있다
	 * @param candidateRank 제외·무작위 선택 전 목록 API 의 순번
	 * @param hoursUnknown  영업 중으로 등록돼 있지만 정규 영업시간이 없다. 쇼핑에서만 true 가 될 수 있다
	 * @param reasons       고른 이유. 순서는 {@link CourseReasons} 가 정하고 문장은 FE 가 만든다
	 */
	public record Stop(TimeSlot slot, String visitTime, String leaveTime, int walkMinutes, int candidateRank,
		Item place, boolean hoursUnknown, List<CourseReason> reasons) {

		public Stop {
			reasons = List.copyOf(reasons);
		}

		/** 도보 거리는 직선거리의 1.3배로 본다. 논문 4.7절과 같은 가정이다. */
		static final double DETOUR = 1.3;
		/** 분당 걷는 거리(m). 논문 4.7절과 같은 가정이다. */
		static final double WALK_METERS_PER_MINUTE = 67;

		static Stop of(TimeSlot slot, LocalTime visitTime, int stayMinutes, int candidateRank, Item place,
			boolean hoursUnknown) {
			return new Stop(slot, visitTime.toString(), visitTime.plusMinutes(stayMinutes).toString(),
				walkMinutes(place.distanceMeters()), candidateRank, place, hoursUnknown, List.of());
		}

		Stop withReasons(List<CourseReason> reasons) {
			return new Stop(slot, visitTime, leaveTime, walkMinutes, candidateRank, place, hoursUnknown, reasons);
		}

		/** 0m 여도 1분으로 보여 준다. */
		static int walkMinutes(double distanceMeters) {
			return Math.max(1, (int) Math.ceil(distanceMeters * DETOUR / WALK_METERS_PER_MINUTE));
		}
	}

	/** 이어갈 곳이 없는 첫 구간. 매장 부족, 영업 근거 부족, 외부 조회 실패를 구분한다. */
	public record Interruption(TimeSlot slot, String reason) {
	}
}
