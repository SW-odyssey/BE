package com.odyssey.recommendation.v2;

import java.util.List;

import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.StoreKind;

/** 지역 탐색 결과. total은 페이지로 자르기 전 조건에 맞는 매장 수다. */
public record V2AreaRecommendationResponse(String status, long total, int page, int size,
	boolean hasNext, V2Context context, List<Item> items) {

	public record Item(long id, String name, String cellId, Area areaId, StoreKind kind,
		double lat, double lng, String googlePlaceId) {
	}
}
