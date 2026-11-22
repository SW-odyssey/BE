package com.odyssey.recommendation.v2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	List<Selected> near(TimeSlot slot, Set<StoreKind> kinds, GeoPoint origin, double minPercentile) {
		return V2RecommendationSelector.rankNearby(eligible(slot, kinds, minPercentile), origin);
	}

	private List<Candidate> eligible(TimeSlot slot, Set<StoreKind> kinds, double minPercentile) {
		return bySlot.getOrDefault(slot, List.of()).stream()
			.filter(candidate -> kinds.contains(candidate.place().kind()))
			.filter(candidate -> candidate.percentile() >= minPercentile)
			.toList();
	}

	LegCandidates forLeg(CourseSchedule.CourseSlot slot, double minimum, double radius, double avoidDistance) {
		return new LegCandidates(eligible(slot.slot(), slot.kinds(), slot.minPercentile()), minimum, radius, avoidDistance);
	}

	static final class LegCandidates {
		private final List<Candidate> candidates;
		private final double minimum;
		private final double radius;
		private final double avoidDistance;
		private final Map<GeoPoint, List<Selected>> rankedByOrigin = new HashMap<>();

		private LegCandidates(List<Candidate> candidates, double minimum, double radius, double avoidDistance) {
			this.candidates = candidates;
			this.minimum = minimum;
			this.radius = radius;
			this.avoidDistance = avoidDistance;
		}

		List<Selected> near(GeoPoint origin, List<GeoPoint> earlierStops, Set<Long> excluded) {
			List<Selected> ranked = rankedByOrigin.computeIfAbsent(origin,
				point -> V2RecommendationSelector.rankWithin(candidates, point, minimum, radius));
			return ranked.stream()
				.filter(selected -> !excluded.contains(selected.candidate().place().id()))
				.filter(selected -> avoidsEarlierStops(selected, earlierStops))
				.limit(V2RecommendationSelector.MAX_RESULTS)
				.toList();
		}

		private boolean avoidsEarlierStops(Selected selected, List<GeoPoint> earlierStops) {
			if (avoidDistance == 0 || earlierStops.isEmpty()) {
				return true;
			}
			GeoPoint location = selected.location();
			return earlierStops.stream().noneMatch(point -> point.distanceTo(location) < avoidDistance);
		}
	}
}
