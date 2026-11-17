package com.odyssey.recommendation.v2;

import java.util.List;

public record V2RecommendationResponse(String status, int total, V2Context context, List<Item> items) {
	public record Item(long id, String name, String cellId, String kind, double lat, double lng,
		double distanceMeters, String googlePlaceId) {}
}
