package com.odyssey.recommendation.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/**
 * 추천 후보에서 목록에 보여 줄 매장을 고른다. DB 나 Spring 을 모르는 순수한 계산이다.
 *
 * <p>추천 규칙의 숫자(반경 300m, 최대 30곳, 방문 백분위 하한 75)는 이 클래스에만 있다. 하한은 후보를
 * 조회하는 SQL 이 적용하므로 여기서 다시 거르지 않고, 서비스가 값만 넘긴다.
 *
 * <p>순서는 칸의 방문 백분위 내림차순 → 반올림하지 않은 직선거리 오름차순 → 관리번호 오름차순이다.
 * 같은 칸의 매장도 각각 한 자리를 차지한다.
 */
final class V2RecommendationSelector {

	static final double RADIUS_METERS = 300;
	static final int MAX_RESULTS = 30;
	static final double MIN_VISITOR_PERCENTILE = 75;

	private static final Comparator<Selected> ORDER = Comparator
		.comparingDouble((Selected selected) -> selected.candidate().percentile()).reversed()
		.thenComparingDouble(Selected::distanceMeters)
		.thenComparing(selected -> selected.candidate().mgmtNo());

	/**
	 * 고른 매장 한 곳.
	 *
	 * @param candidate      후보
	 * @param distanceMeters 사용자 위치에서 매장까지의 직선거리. 반올림하지 않은 값이다
	 */
	record Selected(Candidate candidate, double distanceMeters) {
		static Selected from(Candidate candidate, GeoPoint origin) {
			Place place = candidate.place();
			GeoPoint location = new GeoPoint(place.lat(), place.lng());
			return new Selected(candidate, origin.distanceTo(location));
		}

		GeoPoint location() {
			return new GeoPoint(candidate.place().lat(), candidate.place().lng());
		}
	}

	private V2RecommendationSelector() {
	}

	/**
	 * 요청 조건에 맞고 반경 안에 있는 후보를 순서대로 최대 {@link #MAX_RESULTS} 곳 고른다.
	 * 입력 목록은 바꾸지 않는다.
	 */
	static List<Selected> select(List<Candidate> candidates, V2RecommendationQuery query) {
		List<Candidate> matching = candidates.stream()
			.filter(candidate -> matches(candidate.place(), query))
			.toList();
		return rankNearby(matching, query.origin());
	}

	static List<Selected> rankNearby(List<Candidate> candidates, GeoPoint origin) {
		return rankWithin(candidates, origin, 0, RADIUS_METERS).stream().limit(MAX_RESULTS).toList();
	}

	/** @return 출발점에서 minimum 이상 radius 이하 거리의 후보를 목록 API 와 같은 순서로. 개수 제한은 없다 */
	static List<Selected> rankWithin(List<Candidate> candidates, GeoPoint origin, double minimum, double radius) {
		return candidates.stream()
			.map(candidate -> Selected.from(candidate, origin))
			.filter(selected -> selected.distanceMeters() >= minimum && selected.distanceMeters() <= radius)
			.sorted(ORDER)
			.toList();
	}

	private static boolean matches(Place place, V2RecommendationQuery query) {
		return (query.category() == null || place.kind().category() == query.category())
			&& (query.kind() == null || place.kind() == query.kind())
			&& place.name().toLowerCase(Locale.ROOT)
				.contains(query.q().toLowerCase(Locale.ROOT));
	}
}
