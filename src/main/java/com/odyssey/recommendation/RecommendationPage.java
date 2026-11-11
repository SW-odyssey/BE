package com.odyssey.recommendation;

import java.util.List;
import com.odyssey.store.repository.StoreCardRow;

/** 정렬된 후보에서 한 페이지를 정하고, 목록과 다음 페이지 여부를 함께 유지한다. */
record RecommendationPage(int number, List<StoreCardRow> stores, boolean hasNext) {
	private static final int SIZE = 30;

	RecommendationPage {
		stores = List.copyOf(stores);
	}

	static RecommendationPage from(List<StoreCardRow> ranked, int number) {
		if (number < 0) {
			throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다");
		}
		// int 최대 페이지도 곱셈이 넘치지 않게 범위 확인 전까지 long으로 계산한다.
		long offset = (long) number * SIZE;
		if (offset >= ranked.size()) {
			return new RecommendationPage(number, List.of(), false);
		}
		int start = (int) offset;
		int end = (int) Math.min(offset + SIZE, ranked.size());
		return new RecommendationPage(number, ranked.subList(start, end), end < ranked.size());
	}
}
