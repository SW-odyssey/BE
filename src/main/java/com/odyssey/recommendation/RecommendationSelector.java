package com.odyssey.recommendation;

import java.util.*;
import com.odyssey.cellslotscore.repository.RankedCell;
import com.odyssey.store.repository.StoreCardRow;

/** DB와 무관한 후보 선별. 백분위는 매장 수가 아닌 조건에 맞는 칸 수로 계산한다. */
final class RecommendationSelector {
	static final int MAX_RESULTS = 30;
	private static final double RADIUS_METERS = 500;
	private RecommendationSelector() {
	}

	static List<StoreCardRow> select(List<RankedCell> cells, List<StoreCardRow> stores,
		Double lat, Double lng) {
		Map<Long, Double> scores = new HashMap<>();
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
			order = Comparator.comparingInt((StoreCardRow s) ->
					distance.get(s.id()) <= RADIUS_METERS ? bands.get(s.cellId()) : 5)
				.thenComparingDouble(s -> distance.get(s.id())).thenComparing(StoreCardRow::id);
		}
		return stores.stream().filter(s -> scores.containsKey(s.cellId()))
			.sorted(order).limit(MAX_RESULTS).toList();
	}

	static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
		double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
		return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
	}
}
