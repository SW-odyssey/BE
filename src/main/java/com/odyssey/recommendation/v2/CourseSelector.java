package com.odyssey.recommendation.v2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;

interface CourseSelector {
	Selection select(Request request, CandidatePool pool);

	/**
	 * @param excludeIds     모든 자리에서 뺄 매장
	 * @param previousStopIds 직전 코스의 매장. 재생성 후보의 새 매장 수를 셀 때 사용한다
	 * @param excludedBySlot 자리 번호(position) → 그 자리에서만 뺄 매장. 한 매장이 점심에는 닫혀도 저녁에는 열 수
	 *                       있어서, 영업 확인에 실패한 매장은 실패한 자리에만 적용한다
	 */
	record Request(List<CourseSlot> slots, GeoPoint origin, Set<Long> excludeIds, Weekday weekday, long seed,
		WalkPreference walk, Set<Long> previousStopIds, Map<Integer, Set<Long>> excludedBySlot) {
		public Request {
			slots = List.copyOf(slots);
			excludeIds = Set.copyOf(excludeIds);
			previousStopIds = Set.copyOf(previousStopIds);
			Map<Integer, Set<Long>> copy = new HashMap<>();
			excludedBySlot.forEach((position, ids) -> copy.put(position, Set.copyOf(ids)));
			excludedBySlot = Map.copyOf(copy);
		}

		public Request(List<CourseSlot> slots, GeoPoint origin, Set<Long> excludeIds, Weekday weekday, long seed,
			WalkPreference walk) {
			this(slots, origin, excludeIds, weekday, seed, walk, Map.of());
		}

		public Request(List<CourseSlot> slots, GeoPoint origin, Set<Long> excludeIds, Weekday weekday, long seed,
			WalkPreference walk, Map<Integer, Set<Long>> excludedBySlot) {
			this(slots, origin, excludeIds, weekday, seed, walk, Set.of(), excludedBySlot);
		}

		/** @return 이 자리에서 뺄 매장: 모든 자리에서 뺄 매장과 이 자리에서만 뺄 매장을 합친 것 */
		Set<Long> excludedAt(int position) {
			Set<Long> here = excludedBySlot.getOrDefault(position, Set.of());
			if (here.isEmpty()) {
				return excludeIds;
			}
			Set<Long> excluded = new HashSet<>(excludeIds);
			excluded.addAll(here);
			return excluded;
		}

		CourseSlot slotAt(int position) {
			return slots.stream().filter(slot -> slot.position() == position).findFirst().orElseThrow();
		}
	}

	record Selection(List<Pick> picks, List<Skipped> skipped, Interruption interruption) {
		public Selection {
			picks = List.copyOf(picks);
			skipped = List.copyOf(skipped);
		}
	}

	/**
	 * @param bandSize   고른 후보가 속한 점수대(1위와 3점 이내)의 후보 수. 멈췄으면 0
	 * @param percentile 고른 후보가 있는 칸의 방문 백분위. 멈췄으면 0
	 */
	record Pick(Stop stop, String reason, int bandSize, double percentile) {
		static Pick chosen(CourseSlot slot, int rank, Item item, boolean hoursUnknown, int bandSize,
			double percentile) {
			Stop stop = Stop.of(slot.position(), slot.slot(), slot.visitTime(), slot.stayMinutes(item.kind()),
				rank, item, hoursUnknown);
			return new Pick(stop, null, bandSize, percentile);
		}

		static Pick stopped(String reason) {
			return new Pick(null, reason, 0, 0);
		}
	}
}
