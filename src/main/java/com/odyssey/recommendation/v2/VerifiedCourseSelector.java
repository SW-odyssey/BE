package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
		return new VisitVerification(request).selectVerifiedCourse(pool);
	}

	private final class VisitVerification {
		private final Request request;
		private final Set<Long> excluded;
		private final List<CourseSlot> remaining;
		private final Map<String, Hours> hoursByPlace = new HashMap<>();
		private final List<Skipped> unavailableSlots = new ArrayList<>();

		VisitVerification(Request request) {
			this.request = request;
			this.excluded = new HashSet<>(request.excludeIds());
			this.remaining = new ArrayList<>(request.slots());
		}

		Selection selectVerifiedCourse(CandidatePool pool) {
			Optional<Selection> result;
			do {
				Request retry = new Request(remaining, request.origin(), excluded, request.weekday(), request.seed(), request.walk());
				result = verify(planner.select(retry, pool));
			} while (result.isEmpty());
			return result.orElseThrow();
		}

		private Optional<Selection> verify(Selection plan) {
			List<Pick> verified = new ArrayList<>();
			for (Pick pick : plan.picks()) {
				Stop stop = pick.stop();
				Item place = stop.place();
				CourseSlot slot = request.slotAt(stop.position());
				String placeId = place.googlePlaceId();
				if (placeId == null || placeId.isBlank()) {
					excluded.add(place.id());
					return Optional.empty();
				}
				if (!hoursByPlace.containsKey(placeId) && hoursByPlace.size() >= MAX_LOOKUPS) {
					return Optional.of(interrupted(plan, verified, slot, "CHECK_LIMIT"));
				}
				Hours hours = hoursByPlace.computeIfAbsent(placeId, lookup::load);
				Result result = hours.during(request.weekday(), slot.visitTime(), slot.stayMinutes(place.kind()));
				if (result == Result.UNAVAILABLE) {
					if (!slot.requirement().skipWhenMissing()) {
						return Optional.of(interrupted(plan, verified, slot, "VERIFICATION_UNAVAILABLE"));
					}
					remaining.remove(slot);
					unavailableSlots.add(new Skipped(slot.slot(), slot.position(), "VERIFICATION_UNAVAILABLE"));
					return Optional.empty();
				}
				if (!result.allowsVisit(place.kind())) {
					excluded.add(place.id());
					return Optional.empty();
				}
				verified.add(Pick.chosen(slot, stop.candidateRank(), place, result == Result.HOURS_UNKNOWN,
					pick.bandSize(), pick.percentile()));
			}
			int end = firstUnfilledRequiredPosition(plan);
			return Optional.of(new Selection(verified, skipsBefore(plan, end), plan.interruption()));
		}

		private int firstUnfilledRequiredPosition(Selection plan) {
			if (plan.interruption() == null) {
				return Integer.MAX_VALUE;
			}
			int processedSlots = plan.picks().size() + plan.skipped().size();
			return remaining.get(processedSlots).position();
		}

		private Selection interrupted(Selection plan, List<Pick> verified, CourseSlot slot, String reason) {
			return new Selection(verified, skipsBefore(plan, slot.position()), new Interruption(slot.slot(), reason));
		}

		private List<Skipped> skipsBefore(Selection plan, int position) {
			List<Skipped> skipped = new ArrayList<>(plan.skipped());
			skipped.addAll(unavailableSlots);
			return skipped.stream()
				.filter(slot -> slot.position() < position)
				.sorted(Comparator.comparingInt(Skipped::position))
				.toList();
		}
	}
}
