package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.odyssey.recommendation.v2.CourseHoursLookup.Hours;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;

final class VerifiedCourseSelector implements CourseSelector {

	static final int MAX_LOOKUPS = 40;

	private final CourseSelector planner;
	private final CourseHoursLookup lookup;

	VerifiedCourseSelector(CourseSelector planner, CourseHoursLookup lookup) {
		this.planner = planner;
		this.lookup = lookup;
	}

	@Override
	public Selection select(Request request, CandidatePool pool) {
		Set<Long> excluded = new HashSet<>(request.excludeIds());
		Map<String, Hours> hoursByPlace = new HashMap<>();
		while (true) {
			Selection plan = planner.select(request.excluding(excluded), pool);
			Verification verification = verify(request, plan, hoursByPlace);
			if (verification.closedStore() == null) {
				return verification.selection();
			}
			excluded.add(verification.closedStore());
		}
	}

	private Verification verify(Request request, Selection plan, Map<String, Hours> hoursByPlace) {
		List<Pick> verified = new ArrayList<>();
		for (Pick pick : plan.picks()) {
			Stop stop = pick.stop();
			Item place = stop.place();
			CourseSlot slot = request.slotAt(stop.position());
			String placeId = place.googlePlaceId();
			if (placeId == null || placeId.isBlank()) {
				return Verification.closed(place.id());
			}
			if (!hoursByPlace.containsKey(placeId) && hoursByPlace.size() >= MAX_LOOKUPS) {
				return interrupted(verified, plan, slot, "CHECK_LIMIT");
			}
			Hours hours = hoursByPlace.computeIfAbsent(placeId, lookup::load);
			Result result = hours.during(request.weekday(), slot.visitTime(), slot.stayMinutes(place.kind()));
			if (result == Result.UNAVAILABLE) {
				return interrupted(verified, plan, slot, "VERIFICATION_UNAVAILABLE");
			}
			if (!result.allowsVisit(place.kind())) {
				return Verification.closed(place.id());
			}
			verified.add(Pick.chosen(slot, stop.candidateRank() - 1, place, result == Result.HOURS_UNKNOWN,
				pick.bandSize(), pick.percentile()));
		}
		return Verification.finished(new Selection(verified, plan.skipped(), plan.interruption()));
	}

	private Verification interrupted(List<Pick> verified, Selection plan, CourseSlot slot, String reason) {
		List<Skipped> earlier = plan.skipped().stream().filter(skip -> skip.position() < slot.position()).toList();
		return Verification.finished(new Selection(verified, earlier, new Interruption(slot.slot(), reason)));
	}

	private record Verification(Selection selection, Long closedStore) {
		static Verification closed(long id) {
			return new Verification(null, id);
		}

		static Verification finished(Selection selection) {
			return new Verification(selection, null);
		}
	}
}
