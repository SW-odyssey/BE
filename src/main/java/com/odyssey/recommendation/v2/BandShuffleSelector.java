package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;

final class BandShuffleSelector implements CourseSelector {

	private final CourseVisitChecker checker;

	BandShuffleSelector(CourseVisitChecker checker) {
		this.checker = checker;
	}

	@Override
	public Selection select(Request request, CandidatePool pool) {
		Random random = new Random(request.seed());
		GeoPoint origin = request.origin();
		Set<Long> excluded = new HashSet<>(request.excludeIds());
		List<Pick> picks = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		Interruption interruption = null;
		for (CourseSlot slot : request.slots()) {
			List<Selected> ranked = pool.near(slot.slot(), slot.kinds(), origin, slot.minPercentile());
			Pick pick = pick(ranked, excluded, request.weekday(), slot, random);
			if (pick.stop() == null) {
				if (slot.requirement().skipWhenMissing()) {
					skipped.add(new Skipped(slot.slot(), slot.position(), pick.reason()));
					continue;
				}
				interruption = new Interruption(slot.slot(), pick.reason());
				break;
			}
			picks.add(pick);
			Item place = pick.stop().place();
			excluded.add(place.id());
			origin = new GeoPoint(place.lat(), place.lng());
		}
		return new Selection(picks, skipped, interruption);
	}

	private Pick pick(List<Selected> ranked, Set<Long> excluded, Weekday weekday, CourseSlot slot, Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		Set<Integer> skipped = excludedIndices(ranked, excluded);
		if (skipped.size() == ranked.size()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		List<Double> percentiles = ranked.stream().map(s -> s.candidate().percentile()).toList();
		for (CheckOrder order = CheckOrder.of(percentiles, skipped, random); order.hasNext(); ) {
			int i = order.next();
			Item item = Item.from(ranked.get(i));
			Result result = checker.check(item.googlePlaceId(), weekday, slot.visitTime(),
				slot.stayMinutes(item.kind()));
			if (result.allowsVisit(item.kind())) {
				return Pick.chosen(slot, i + 1, item, result == Result.HOURS_UNKNOWN, order.bandSize(), percentiles.get(i));
			}
			if (result == Result.UNAVAILABLE) {
				return Pick.stopped("VERIFICATION_UNAVAILABLE");
			}
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	private static Set<Integer> excludedIndices(List<Selected> ranked, Set<Long> excluded) {
		Set<Integer> skipped = new HashSet<>();
		for (int i = 0; i < ranked.size(); i++) {
			if (excluded.contains(ranked.get(i).candidate().place().id())) {
				skipped.add(i);
			}
		}
		return skipped;
	}
}
