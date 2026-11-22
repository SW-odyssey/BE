package com.odyssey.recommendation.v2;

import java.util.List;
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

	record Request(List<CourseSlot> slots, GeoPoint origin, Set<Long> excludeIds, Weekday weekday, long seed) {
		public Request {
			slots = List.copyOf(slots);
			excludeIds = Set.copyOf(excludeIds);
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
		static Pick chosen(CourseSlot slot, int index, Item item, boolean hoursUnknown, int bandSize,
			double percentile) {
			return new Pick(Stop.of(slot.position(), slot.slot(), slot.visitTime(), slot.stayMinutes(item.kind()), index + 1, item,
				hoursUnknown), null, bandSize, percentile);
		}

		static Pick stopped(String reason) {
			return new Pick(null, reason, 0, 0);
		}
	}
}
