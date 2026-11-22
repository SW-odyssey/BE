package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.HashSet;
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
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseReasons.StopFacts;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;

/**
 * 구간마다 기존 목록 API 의 후보에서 한 곳을 골라 심야까지 잇는다.
 *
 * <p>확인할 후보 순서는 {@link CheckOrder} 가 정한다. 뒤 구간이 끊겨도 앞에서 고른 매장을 되돌리지 않는다.
 * 코스를 다 고른 뒤 고른 칸들의 이유 재료를 한 번 읽어 정류장마다 이유를 붙인다.
 */
@Service
public class V2CourseService {

	private static final Logger log = LoggerFactory.getLogger(V2CourseService.class);

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
		return recommend(datasetId, weekday, startSlot, origin, excludeIds, seed, afternoon, false);
	}

	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed, StoreKind afternoon, boolean lateNight) {
		List<CourseSlot> slots = CourseSchedule.slots(weekday, afternoon, startSlot, lateNight);
		List<TimeSlot> timeSlots = slots.stream().map(CourseSlot::slot).distinct().toList();
		CandidatePool pool = candidates.load(datasetId, weekday, startSlot, timeSlots);
		Selection selection = selectStops(pool, slots, origin, excludeIds, weekday, new Random(seed));
		return response(pool.context(), seed, selection);
	}

	private record Selection(List<Pick> picks, List<Skipped> skipped, Interruption interruption) {
	}

	private Selection selectStops(CandidatePool pool, List<CourseSlot> slots, GeoPoint origin, Set<Long> excludeIds,
		Weekday weekday, Random random) {
		Set<Long> excluded = new HashSet<>(excludeIds);
		List<Pick> picks = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		Interruption interruption = null;
		for (CourseSlot slot : slots) {
			List<Selected> ranked = pool.near(slot.slot(), slot.kinds(), origin, slot.minPercentile());
			Pick pick = pick(ranked, excluded, weekday, slot, random);
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

	private V2CourseResponse response(V2Context context, long seed, Selection selection) {
		List<Pick> picks = selection.picks();
		Facts facts = facts(context.datasetId(), context.weekday(), picks);
		List<Stop> stops = picks.stream().map(pick -> withReasons(pick, facts)).toList();
		String status = selection.interruption() == null ? "COMPLETE" : stops.isEmpty() ? "NO_MATCHES" : "PARTIAL";
		return new V2CourseResponse(status, context, seed, stops, selection.skipped(), selection.interruption());
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
			if (result == Result.OPEN) {
				return Pick.chosen(slot, i, item, false, order.bandSize(), percentiles.get(i));
			}
			// 작은 공방과 체험관은 정규 영업시간이 등록되지 않은 곳이 많다.
			if (result == Result.HOURS_UNKNOWN && acceptsUnknownHours(item.kind())) {
				return Pick.chosen(slot, i, item, true, order.bandSize(), percentiles.get(i));
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

	private static boolean acceptsUnknownHours(StoreKind kind) {
		return kind == StoreKind.SHOP || kind == StoreKind.PLAY;
	}

	/**
	 * @param bandSize   고른 후보가 속한 점수대(1위와 3점 이내)의 후보 수. 멈췄으면 0
	 * @param percentile 고른 후보가 있는 칸의 방문 백분위. 멈췄으면 0
	 */
	private record Pick(Stop stop, String reason, int bandSize, double percentile) {
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
