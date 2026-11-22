package com.odyssey.recommendation.v2;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
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

	V2CourseService(CourseCandidates candidates, CourseVisitChecker checker) {
		this.candidates = candidates;
		this.checker = checker;
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
		List<Stage> stages = planStagesFrom(startSlot, afternoon);
		CandidatePool pool = loadCandidates(datasetId, weekday, stages);
		return buildCourse(stages, pool, weekday, origin, excludeIds, seed);
	}

	private static void validateAfternoonKind(StoreKind afternoon) {
		if (afternoon != StoreKind.CAFE && afternoon != StoreKind.SHOP) {
			throw new IllegalArgumentException("afternoon: " + afternoon);
		}
	}

	private static List<Stage> planStagesFrom(TimeSlot startSlot, StoreKind afternoon) {
		List<Stage> remainingStages = STAGES.stream()
			.dropWhile(stage -> stage.slot() != startSlot)
			.toList();
		return remainingStages.stream()
			.map(stage -> stage.withAfternoonKind(afternoon))
			.toList();
	}

	private CandidatePool loadCandidates(String datasetId, Weekday weekday, List<Stage> stages) {
		List<TimeSlot> slots = stages.stream().map(Stage::slot).toList();
		return candidates.load(datasetId, weekday, slots);
	}

	private V2CourseResponse buildCourse(List<Stage> stages, CandidatePool pool, Weekday weekday,
		GeoPoint origin, Set<Long> excludeIds, long seed) {
		var random = new Random(seed);
		var excluded = new HashSet<>(excludeIds);
		List<Stop> stops = new ArrayList<>();
		for (Stage stage : stages) {
			List<Selected> nearby = pool.near(stage.slot(), stage.kind(), origin);
			Pick pick = pickStageStop(nearby, excluded, weekday, stage, random);
			if (pick.stop() == null) {
				var interruption = new Interruption(stage.slot(), pick.reason());
				return V2CourseResponse.interrupted(pool.context(), seed, stops, interruption);
			}
			stops.add(pick.stop());
			Item place = pick.stop().place();
			excluded.add(place.id());
			origin = new GeoPoint(place.lat(), place.lng());
		}
		return V2CourseResponse.complete(pool.context(), seed, stops);
	}

	private Pick pickStageStop(List<Selected> ranked, Set<Long> excluded, Weekday weekday, Stage stage, Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		Set<Integer> skipped = excludedIndices(ranked, excluded);
		if (skipped.size() == ranked.size()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		List<Double> percentiles = ranked.stream().map(s -> s.candidate().percentile()).toList();
		CheckOrder order = CheckOrder.of(percentiles, skipped, random);
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

	private Pick findFirstVisitableStop(List<Selected> ranked, Weekday weekday, Stage stage, CheckOrder order) {
		while (order.hasNext()) {
			int index = order.next();
			Item item = Item.from(ranked.get(index));
			Result result = checker.check(item.googlePlaceId(), weekday, stage.visitTime(), stage.stayMinutes());
			if (stage.allowsVisit(result)) {
				return Pick.selected(stage, index + 1, item, result);
			}
			if (result == Result.UNAVAILABLE) {
				return Pick.stopped("VERIFICATION_UNAVAILABLE");
			}
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	private record Stage(TimeSlot slot, StoreKind kind, LocalTime visitTime, int stayMinutes) {
		boolean allowsVisit(Result result) {
			// 작은 공방·갤러리는 Google에 영업시간이 없는 곳이 많아 쇼핑 구간에서만 허용한다.
			return result == Result.OPEN || (kind == StoreKind.SHOP && result == Result.HOURS_UNKNOWN);
		}

		Stage withAfternoonKind(StoreKind afternoon) {
			if (slot == TimeSlot.AFTERNOON && afternoon == StoreKind.SHOP) {
				return new Stage(slot, StoreKind.SHOP, visitTime, SHOP_STAY_MINUTES);
			}
			return this;
		}
	}

	private record Pick(Stop stop, String reason) {
		static Pick selected(Stage stage, int rank, Item item, Result result) {
			var stop = new Stop(stage.slot(), stage.visitTime().toString(), rank, item, result == Result.HOURS_UNKNOWN);
			return new Pick(stop, null);
		}

		static Pick stopped(String reason) {
			return new Pick(null, reason);
		}
	}
}
