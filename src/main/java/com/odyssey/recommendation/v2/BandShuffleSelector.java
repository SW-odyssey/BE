package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

/**
 * 자리마다 앞 구간에서 고른 매장 근처의 후보를 방문 백분위 점수대로 묶어 섞고, 영업하는 첫 후보를 고른다.
 */
final class BandShuffleSelector implements CourseSelector {

	/** 90점 이상 칸의 인접 월 백분위 변화는 중앙값 1.3점, 75%가 2.8점 이내였다(2025년 12개 배치). */
	static final double PERCENTILE_BAND = 3.0;
	/**
	 * 5번일 때 을지로 토요일 심야에서 3점 범위 7곳 중 영업하는 2곳에 닿기 전에 끊겼다(2026-11-21).
	 * 범위 하나는 대개 10곳 이하라 범위 전체를 확인할 수 있게 잡았다.
	 */
	static final int MAX_CHECKS = 10;

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
			Pick pick = pickStageStop(ranked, unavailableAt(request, slot, excluded), request.weekday(), slot, random);
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

	/** 이미 고른 매장과 요청이 뺀 매장에, 이 자리에서만 뺄 매장을 더한다. */
	private static Set<Long> unavailableAt(Request request, CourseSlot slot, Set<Long> alreadyExcluded) {
		Set<Long> unavailable = new HashSet<>(alreadyExcluded);
		unavailable.addAll(request.excludedAt(slot.position()));
		return unavailable;
	}

	private Pick pickStageStop(List<Selected> ranked, Set<Long> excluded, Weekday weekday, CourseSlot slot,
		Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		Set<Integer> skipped = excludedIndices(ranked, excluded);
		if (skipped.size() == ranked.size()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		List<Double> percentiles = ranked.stream().map(s -> s.candidate().percentile()).toList();
		Iterator<BandCandidate> order = candidateCheckOrder(percentiles, skipped, random);
		return findFirstVisitableStop(ranked, weekday, slot, order);
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

	/** 앞 구간에서 쓰고 남은 난수를 다음 구간이 이어 쓰므로 실제 확인할 점수대만 섞는다. */
	static Iterator<BandCandidate> candidateCheckOrder(List<Double> percentiles, Set<Integer> excluded,
		Random random) {
		return groupByPercentileBand(percentiles, excluded).stream()
			.flatMap(band -> {
				Collections.shuffle(band, random);
				return band.stream().map(index -> new BandCandidate(index, band.size()));
			})
			.limit(MAX_CHECKS)
			.iterator();
	}

	/** 백분위 내림차순에서 제외된 매장을 건너뛴 뒤 각 점수대의 첫 후보가 하한을 정한다. */
	static List<List<Integer>> groupByPercentileBand(List<Double> percentiles, Set<Integer> excluded) {
		List<List<Integer>> bands = new ArrayList<>();
		double minimumPercentile = Double.POSITIVE_INFINITY;
		for (int index = 0; index < percentiles.size(); index++) {
			if (excluded.contains(index)) {
				continue;
			}
			double percentile = percentiles.get(index);
			if (bands.isEmpty() || percentile < minimumPercentile) {
				bands.add(new ArrayList<>());
				minimumPercentile = percentile - PERCENTILE_BAND;
			}
			bands.getLast().add(index);
		}
		return bands;
	}

	private Pick findFirstVisitableStop(List<Selected> ranked, Weekday weekday, CourseSlot slot,
		Iterator<BandCandidate> order) {
		while (order.hasNext()) {
			BandCandidate candidate = order.next();
			Selected selected = ranked.get(candidate.index());
			Item item = Item.from(selected);
			Result result = checker.check(item.googlePlaceId(), weekday, slot.visitTime(),
				slot.stayMinutes(item.kind()));
			if (result.allowsVisit(item.kind())) {
				return Pick.chosen(slot, candidate.index() + 1, item, result == Result.HOURS_UNKNOWN,
					candidate.bandSize(), selected.candidate().percentile());
			}
			if (result == Result.UNAVAILABLE) {
				return Pick.stopped("VERIFICATION_UNAVAILABLE");
			}
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	record BandCandidate(int index, int bandSize) {
	}
}
