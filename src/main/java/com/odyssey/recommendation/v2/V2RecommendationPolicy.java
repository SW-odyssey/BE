package com.odyssey.recommendation.v2;

/** v2 추천에서 공유하는 기본 방문 기준. 코스는 일정에 따라 별도 하한을 적용할 수 있다. */
final class V2RecommendationPolicy {

	static final double DEFAULT_MIN_VISITOR_PERCENTILE = 75;

	private V2RecommendationPolicy() {
	}
}
