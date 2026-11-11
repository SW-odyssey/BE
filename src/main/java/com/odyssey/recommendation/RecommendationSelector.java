package com.odyssey.recommendation;

import java.util.*;
import com.odyssey.cellslotscore.repository.RankedCell;
import com.odyssey.store.repository.StoreCardRow;

/** DB와 무관한 후보 선별. 백분위는 매장 수가 아닌 조건에 맞는 칸 수로 계산한다. */
final class RecommendationSelector {
	/** 사용자 위치 주변에서 먼저 후보를 채우는 반경. 단위는 m이다. */
	private static final double RADIUS_METERS = 500;

	/** 상태 없이 사용하는 선별 함수 모음이므로 인스턴스를 만들지 않는다. */
	private RecommendationSelector() {
	}

	/**
	 * 전체 후보를 백분위 구간과 거리로 정렬한다.
	 *
	 * <p>500m 안에서는 80+ · 60+ · 40+ · 20+ · 전체 구간 순으로, 같은 구간은 거리순으로 담는다.
	 * 부족하면 500m 밖 후보를 거리순으로 채운다. 위치가 없으면 점수순, 동점은 매장 ID순이다.
	 * 이 순서가 응답 items의 추천 순서이며, 점수 숫자는 응답에 넣지 않는다.
	 *
	 * @param cells  지역 · 붐빔 조건을 거친 후보 칸. 가게 필터 전에 백분위의 모집단을 정한다
	 * @param stores 후보 칸 안에서 분류 · 노포 · 검색어 조건에 맞는 가게
	 * @param lat    서비스 칸 안 사용자 위도(도). 위치 정렬을 쓰지 않으면 null
	 * @param lng    서비스 칸 안 사용자 경도(도). 위치 정렬을 쓰지 않으면 null
	 */
	static List<StoreCardRow> rank(List<RankedCell> cells, List<StoreCardRow> stores,
		Double lat, Double lng) {
		Map<Long, Double> scores = new HashMap<>();
		// band 0~4는 각각 80+ · 60+ · 40+ · 20+ · 나머지 구간이다. 작을수록 우선한다.
		Map<Long, Integer> bands = new HashMap<>();
		var sorted = cells.stream().sorted(Comparator.comparingDouble(RankedCell::score)
			.thenComparing(RankedCell::cellId)).toList();
		// 동점은 평균 순위를 공유한다 (rank(pct=true)의 정의).
		for (int start = 0; start < sorted.size();) {
			int end = start + 1;
			while (end < sorted.size() && sorted.get(end).score() == sorted.get(start).score()) {
				end++;
			}
			double percentile = 100.0 * ((start + 1 + end) / 2.0) / sorted.size();
			int band = percentile >= 80 ? 0 : percentile >= 60 ? 1
				: percentile >= 40 ? 2 : percentile >= 20 ? 3 : 4;
			for (int i = start; i < end; i++) {
				scores.put(sorted.get(i).cellId(), sorted.get(i).score());
				bands.put(sorted.get(i).cellId(), band);
			}
			start = end;
		}
		Comparator<StoreCardRow> order;
		if (lat == null || lng == null) {
			order = Comparator.comparingDouble((StoreCardRow s) -> scores.get(s.cellId()))
				.reversed().thenComparing(StoreCardRow::id);
		} else {
			Map<Long, Double> distance = new HashMap<>();
			stores.forEach(s -> distance.put(s.id(), distanceMeters(lat, lng, s.lat(), s.lng())));
			// 500m 밖은 별도 구간 5로 두어, 안쪽의 모든 점수 구간을 채운 뒤 선택한다.
			order = Comparator.comparingInt((StoreCardRow s) ->
					distance.get(s.id()) <= RADIUS_METERS ? bands.get(s.cellId()) : 5)
				.thenComparingDouble(s -> distance.get(s.id())).thenComparing(StoreCardRow::id);
		}
		return stores.stream().filter(s -> scores.containsKey(s.cellId()))
			.sorted(order).toList();
	}

	/**
	 * 두 위경도(도) 사이 거리를 Haversine 공식으로 구한다.
	 *
	 * @return 지구 평균 반지름 6,371,000m를 사용한 거리(m)
	 */
	static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
		double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
		return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
	}
}
