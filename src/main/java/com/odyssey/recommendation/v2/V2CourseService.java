package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseReasonFacts.Facts;
import com.odyssey.recommendation.v2.CourseReasons.StopFacts;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;

/**
 * 뒤 구간이 끊겨도 앞에서 고른 매장은 코스에 남긴다.
 */
@Service
public class V2CourseService {

	private static final Logger log = LoggerFactory.getLogger(V2CourseService.class);

	/** 90점 이상 칸의 인접 월 백분위 변화는 중앙값 1.3점, 75%가 2.8점 이내였다(2025년 12개 배치). */
	private static final double PERCENTILE_BAND = 3.0;
	/**
	 * 5번일 때 을지로 토요일 심야에서 3점 범위 7곳 중 영업하는 2곳에 닿기 전에 끊겼다(2026-11-21).
	 * 범위 하나는 대개 10곳 이하라 범위 전체를 확인할 수 있게 잡았다.
	 */
	private static final int MAX_CHECKS = 10;

	private final CourseCandidates candidates;
	private final CourseVisitChecker checker;
	private final CourseReasonFacts reasonFacts;

	V2CourseService(CourseCandidates candidates, CourseVisitChecker checker, CourseReasonFacts reasonFacts) {
		this.candidates = candidates;
		this.checker = checker;
		this.reasonFacts = reasonFacts;
	}

	/** 같은 seed·배치·조건이면 같은 순서로 후보를 확인한다. 결과는 영업 확인 시점에 따라 달라질 수 있다. */
	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed) {
		return recommend(datasetId, weekday, startSlot, origin, excludeIds, seed, StoreKind.CAFE);
	}

	/** @param afternoon 오후 자리의 종류. 카페(CAFE) 또는 구경(SHOP) */
	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed, StoreKind afternoon) {
		validateAfternoonKind(afternoon);
		List<CourseSlot> stages = planStagesFrom(startSlot, afternoon);
		CandidatePool pool = loadCandidates(datasetId, weekday, stages);
		return buildCourse(stages, pool, weekday, origin, excludeIds, seed);
	}

	private static void validateAfternoonKind(StoreKind afternoon) {
		if (afternoon != StoreKind.CAFE && afternoon != StoreKind.SHOP) {
			throw new IllegalArgumentException("afternoon: " + afternoon);
		}
	}

	private static List<CourseSlot> planStagesFrom(TimeSlot startSlot, StoreKind afternoon) {
		return CourseSchedule.slots(afternoon).stream().dropWhile(slot -> slot.slot() != startSlot).toList();
	}

	private CandidatePool loadCandidates(String datasetId, Weekday weekday, List<CourseSlot> stages) {
		List<TimeSlot> slots = stages.stream().map(CourseSlot::slot).toList();
		return candidates.load(datasetId, weekday, slots);
	}

	private V2CourseResponse buildCourse(List<CourseSlot> stages, CandidatePool pool, Weekday weekday,
		GeoPoint origin, Set<Long> excludeIds, long seed) {
		Random random = new Random(seed);
		Set<Long> excluded = new HashSet<>(excludeIds);
		List<Pick> picks = new ArrayList<>();
		Interruption interruption = null;
		for (CourseSlot stage : stages) {
			List<Selected> nearby = pool.near(stage.slot(), stage.kinds().iterator().next(), origin);
			Pick pick = pickStageStop(nearby, excluded, weekday, stage, random);
			if (pick.stop() == null) {
				interruption = new Interruption(stage.slot(), pick.reason());
				break;
			}
			picks.add(pick);
			Item place = pick.stop().place();
			excluded.add(place.id());
			origin = new GeoPoint(place.lat(), place.lng());
		}
		// 영업 확인이 다 끝난 뒤라 이 조회는 Google 호출 중에 커넥션을 잡지 않는다
		Facts facts = facts(pool.context().datasetId(), weekday, picks);
		List<Stop> stops = picks.stream().map(pick -> withReasons(pick, facts)).toList();
		return interruption == null
			? V2CourseResponse.complete(pool.context(), seed, stops)
			: V2CourseResponse.interrupted(pool.context(), seed, stops, interruption);
	}

	/** 이유 때문에 코스가 실패하지 않게, 조회가 실패하면 그 재료로 붙는 이유(PEAK_NOW, AREA_TYPE)만 뺀다. */
	private Facts facts(String datasetId, Weekday weekday, List<Pick> picks) {
		if (picks.isEmpty()) {
			return Facts.NONE;
		}
		List<Long> cellIds = picks.stream().map(pick -> cellId(pick.stop())).distinct().toList();
		try {
			return reasonFacts.load(datasetId, weekday, cellIds);
		} catch (DataAccessException e) {
			log.warn("코스 이유 재료 조회 실패. 붐비는 시간대·동네 유형 이유를 뺀다: {}", e.getMessage());
			return Facts.NONE;
		}
	}

	private static Stop withReasons(Pick pick, Facts facts) {
		Stop stop = pick.stop();
		long cellId = cellId(stop);
		return stop.withReasons(CourseReasons.of(new StopFacts(pick.percentile(), stop.slot(),
			facts.peakSlot(cellId), facts.areaType(cellId), stop.visitTime(), stop.leaveTime(), pick.bandSize(),
			stop.hoursUnknown())));
	}

	private static long cellId(Stop stop) {
		return Long.parseLong(stop.place().cellId());
	}

	private Pick pickStageStop(List<Selected> ranked, Set<Long> excluded, Weekday weekday, CourseSlot stage, Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		Set<Integer> skipped = excludedIndices(ranked, excluded);
		if (skipped.size() == ranked.size()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		List<Double> percentiles = ranked.stream().map(s -> s.candidate().percentile()).toList();
		Iterator<BandCandidate> order = candidateCheckOrder(percentiles, skipped, random);
		return findFirstVisitableStop(ranked, weekday, stage, order);
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
	static Iterator<BandCandidate> candidateCheckOrder(List<Double> percentiles, Set<Integer> excluded, Random random) {
		return groupByPercentileBand(percentiles, excluded).stream()
			.flatMap(band -> {
				Collections.shuffle(band, random);
				return band.stream().map(index -> new BandCandidate(index, band.size()));
			})
			.limit(MAX_CHECKS)
			.iterator();
	}

	/** 백분위 내림차순에서 제외된 매장을 건너뛴 뒤 각 점수대의 첫 후보가 하한을 정한다. */
	private static List<List<Integer>> groupByPercentileBand(List<Double> percentiles, Set<Integer> excluded) {
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

	private Pick findFirstVisitableStop(List<Selected> ranked, Weekday weekday, CourseSlot stage, Iterator<BandCandidate> order) {
		while (order.hasNext()) {
			BandCandidate candidate = order.next();
			Selected selected = ranked.get(candidate.index());
			Item item = Item.from(selected);
			Result result = checker.check(item.googlePlaceId(), weekday, stage.visitTime(), stage.stayMinutes(item.kind()));
			if (result == Result.OPEN || (item.kind() == StoreKind.SHOP && result == Result.HOURS_UNKNOWN)) {
				return Pick.selected(stage, candidate, item, result, selected.candidate().percentile());
			}
			if (result == Result.UNAVAILABLE) {
				return Pick.stopped("VERIFICATION_UNAVAILABLE");
			}
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	record BandCandidate(int index, int bandSize) {
	}

	private record Pick(Stop stop, String reason, int bandSize, double percentile) {
		static Pick selected(CourseSlot stage, BandCandidate candidate, Item item, Result result, double percentile) {
			Stop stop = Stop.of(stage.slot(), stage.visitTime(), stage.stayMinutes(item.kind()), candidate.index() + 1, item,
				result == Result.HOURS_UNKNOWN);
			return new Pick(stop, null, candidate.bandSize(), percentile);
		}

		static Pick stopped(String reason) {
			return new Pick(null, reason, 0, 0);
		}
	}
}
