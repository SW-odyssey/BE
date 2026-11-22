package com.odyssey.recommendation.v2;

import java.time.LocalTime;
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
import com.odyssey.recommendation.v2.CourseReasons.StopFacts;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
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

	/**
	 * 머무는 시간(분)은 통계청 생활시간조사 2024 원자료로 20·30대가 기타 실내 한 곳에 이동 없이 머문 구간의
	 * 가중 중앙값이다(저자 계산, 공표 통계 아님). 식사 점심 50·저녁 60, 카페형 80, 오후 시작 쇼핑 30,
	 * 21시 이후 시작 유흥 70. 자정을 넘긴 구간은 빼고 셌으므로 심야 값은 짧은 쪽으로 치우친다.
	 * 심야를 23:30 도착으로 두면 그때 연 주점 40%가 00:40 전에 닫아 Google 표본 30곳 완주가 29→25로
	 * 떨어졌다. U+ 심야(22~01시) 안인 22:30 도착이면 29/30 그대로다(2026-11-21).
	 */
	private static final List<Stage> STAGES = List.of(
		new Stage(TimeSlot.LUNCH, StoreKind.KOREAN, LocalTime.of(12, 30), 50),
		new Stage(TimeSlot.AFTERNOON, StoreKind.CAFE, LocalTime.of(15, 0), 80),
		new Stage(TimeSlot.DINNER, StoreKind.KOREAN, LocalTime.of(19, 0), 60),
		new Stage(TimeSlot.LATE_NIGHT, StoreKind.BAR, LocalTime.of(22, 30), 70));
	static final int SHOP_STAY_MINUTES = 30;

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
		if (afternoon != StoreKind.CAFE && afternoon != StoreKind.SHOP) {
			throw new IllegalArgumentException("afternoon: " + afternoon);
		}
		List<Stage> stages = STAGES.stream().dropWhile(s -> s.slot() != startSlot)
			.map(s -> s.slot() == TimeSlot.AFTERNOON && afternoon == StoreKind.SHOP
				? new Stage(s.slot(), afternoon, s.visitTime(), SHOP_STAY_MINUTES) : s)
			.toList();
		// DB 는 여기서 다 읽고 끝낸다. 아래 영업 확인은 트랜잭션 밖이다
		CandidatePool pool = candidates.load(datasetId, weekday, stages.stream().map(Stage::slot).toList());
		var random = new Random(seed);
		var excluded = new HashSet<>(excludeIds);
		List<Pick> picks = new ArrayList<>();
		Interruption interruption = null;
		for (Stage stage : stages) {
			Pick pick = pick(pool.near(stage.slot(), stage.kind(), origin), excluded, weekday, stage, random);
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
		String status = interruption == null ? "COMPLETE" : stops.isEmpty() ? "NO_MATCHES" : "PARTIAL";
		return new V2CourseResponse(status, pool.context(), seed, stops, interruption);
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

	private Pick pick(List<Selected> ranked, Set<Long> excluded, Weekday weekday, Stage stage, Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		Set<Integer> skipped = new HashSet<>();
		for (int i = 0; i < ranked.size(); i++) {
			if (excluded.contains(ranked.get(i).candidate().place().id())) {
				skipped.add(i);
			}
		}
		if (skipped.size() == ranked.size()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		List<Double> percentiles = ranked.stream().map(s -> s.candidate().percentile()).toList();
		for (var order = CheckOrder.of(percentiles, skipped, random); order.hasNext(); ) {
			int i = order.next();
			Item item = Item.from(ranked.get(i));
			Result result = checker.check(item.googlePlaceId(), weekday, stage.visitTime(), stage.stayMinutes());
			if (result == Result.OPEN) {
				return Pick.chosen(stage, i, item, false, order.bandSize(), percentiles.get(i));
			}
			// 작은 공방·갤러리는 Google 에 영업시간이 없는 곳이 많다. 쇼핑만 받고 응답에 표시한다
			if (result == Result.HOURS_UNKNOWN && stage.kind() == StoreKind.SHOP) {
				return Pick.chosen(stage, i, item, true, order.bandSize(), percentiles.get(i));
			}
			if (result == Result.UNAVAILABLE) {
				return Pick.stopped("VERIFICATION_UNAVAILABLE");
			}
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	private record Stage(TimeSlot slot, StoreKind kind, LocalTime visitTime, int stayMinutes) {
	}

	/**
	 * @param bandSize   고른 후보가 속한 점수대(1위와 3점 이내)의 후보 수. 멈췄으면 0
	 * @param percentile 고른 후보가 있는 칸의 방문 백분위. 멈췄으면 0
	 */
	private record Pick(Stop stop, String reason, int bandSize, double percentile) {
		static Pick chosen(Stage stage, int index, Item item, boolean hoursUnknown, int bandSize,
			double percentile) {
			return new Pick(Stop.of(stage.slot(), stage.visitTime(), stage.stayMinutes(), index + 1, item,
				hoursUnknown), null, bandSize, percentile);
		}

		static Pick stopped(String reason) {
			return new Pick(null, reason, 0, 0);
		}
	}
}
