package com.odyssey.recommendation.v2;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
 * 구간마다 기존 목록 API 의 후보에서 한 곳을 골라 심야까지 잇는다.
 *
 * <p>1위와 방문 백분위 {@link #BAND} 점 이내인 후보는 같은 점수로 보고 seed 로 섞어 하나씩 영업을 확인한다.
 * 그 점수대가 모두 안 맞으면 남은 후보의 다음 점수대로 내려간다. 외부 호출 비용 때문에 구간당 확인은
 * {@link #MAX_CHECKS} 번까지다. 뒤 구간이 끊겨도 앞에서 고른 매장을 되돌리지 않는다.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class V2CourseService {

	/** 90점 이상 칸의 인접 월 백분위 변화는 중앙값 1.3점, 75%가 2.8점 이내였다(2025년 12개 배치). */
	static final double BAND = 3.0;
	/**
	 * 5번일 때 을지로 토요일 심야에서 3점 범위 7곳 중 영업하는 2곳에 닿기 전에 끊겼다(2026-11-21).
	 * 범위 하나는 대개 10곳 이하라 범위 전체를 확인할 수 있게 잡았다.
	 */
	static final int MAX_CHECKS = 10;
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

	private final V2RecommendationService recommendations;
	private final CourseVisitChecker checker;

	V2CourseService(V2RecommendationService recommendations, CourseVisitChecker checker) {
		this.recommendations = recommendations;
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
		if (afternoon != StoreKind.CAFE && afternoon != StoreKind.SHOP) {
			throw new IllegalArgumentException("afternoon: " + afternoon);
		}
		var random = new Random(seed);
		var excluded = new HashSet<>(excludeIds);
		List<Stop> stops = new ArrayList<>();
		V2Context context = null;
		Interruption interruption = null;
		for (Stage base : STAGES.stream().dropWhile(s -> s.slot() != startSlot).toList()) {
			Stage stage = base.slot() == TimeSlot.AFTERNOON && afternoon == StoreKind.SHOP
				? new Stage(base.slot(), afternoon, base.visitTime(), SHOP_STAY_MINUTES) : base;
			var query = new V2RecommendationQuery(datasetId, weekday, stage.slot(), origin,
				stage.kind().category(), stage.kind(), "");
			var ranked = recommendations.rank(query);
			if (context == null) {
				context = ranked.context();
			}
			Pick pick = pick(ranked.selected(), excluded, weekday, stage, random);
			if (pick.stop() == null) {
				interruption = new Interruption(stage.slot(), pick.reason());
				break;
			}
			stops.add(pick.stop());
			Item place = pick.stop().place();
			excluded.add(place.id());
			origin = new GeoPoint(place.lat(), place.lng());
		}
		String status = interruption == null ? "COMPLETE" : stops.isEmpty() ? "NO_MATCHES" : "PARTIAL";
		return new V2CourseResponse(status, context, seed, List.copyOf(stops), interruption);
	}

	private Pick pick(List<Selected> ranked, Set<Long> excluded, Weekday weekday, Stage stage, Random random) {
		if (ranked.isEmpty()) {
			return Pick.stopped("NO_NEARBY_MATCH");
		}
		List<Integer> remaining = new ArrayList<>();
		for (int i = 0; i < ranked.size(); i++) {
			if (!excluded.contains(ranked.get(i).candidate().place().id())) {
				remaining.add(i);
			}
		}
		if (remaining.isEmpty()) {
			return Pick.stopped("ALL_EXCLUDED");
		}
		int checks = 0;
		while (!remaining.isEmpty() && checks < MAX_CHECKS) {
			double floor = percentile(ranked, remaining.getFirst()) - BAND;
			List<Integer> band = remaining.stream().filter(i -> percentile(ranked, i) >= floor).toList();
			List<Integer> shuffled = new ArrayList<>(band);
			Collections.shuffle(shuffled, random);
			for (int i : shuffled) {
				if (checks == MAX_CHECKS) {
					break;
				}
				checks++;
				Item item = Item.from(ranked.get(i));
				Result result = checker.check(item.googlePlaceId(), weekday, stage.visitTime(), stage.stayMinutes());
				if (result == Result.OPEN) {
					return new Pick(new Stop(stage.slot(), stage.visitTime().toString(), i + 1, item, false), null);
				}
				// 작은 공방·갤러리는 Google 에 영업시간이 없는 곳이 많다. 쇼핑만 받고 응답에 표시한다
				if (result == Result.HOURS_UNKNOWN && stage.kind() == StoreKind.SHOP) {
					return new Pick(new Stop(stage.slot(), stage.visitTime().toString(), i + 1, item, true), null);
				}
				if (result == Result.UNAVAILABLE) {
					return Pick.stopped("VERIFICATION_UNAVAILABLE");
				}
			}
			remaining.removeAll(band);
		}
		return Pick.stopped("NO_VERIFIED_MATCH");
	}

	private static double percentile(List<Selected> ranked, int index) {
		return ranked.get(index).candidate().percentile();
	}

	private record Stage(TimeSlot slot, StoreKind kind, LocalTime visitTime, int stayMinutes) {
	}

	private record Pick(Stop stop, String reason) {
		static Pick stopped(String reason) {
			return new Pick(null, reason);
		}
	}
}
