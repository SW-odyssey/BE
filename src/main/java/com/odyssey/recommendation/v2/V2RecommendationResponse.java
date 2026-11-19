package com.odyssey.recommendation.v2;

import java.util.List;

import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/**
 * 추천 목록 응답. items 의 순서가 곧 화면에 보이는 순서라서 FE 는 순서를 바꾸지 않는다.
 *
 * @param status OK 또는 NO_MATCHES. 후자도 정상 응답이고 목록만 비어 있다
 * @param total  이번에 돌려주는 items 의 개수. 전체 후보 수가 아니다
 */
public record V2RecommendationResponse(String status, int total, V2Context context,
	List<Item> items) {

	static final String OK = "OK";
	static final String NO_MATCHES = "NO_MATCHES";

	static V2RecommendationResponse of(V2Context context, List<Selected> selected) {
		List<Item> items = selected.stream().map(Item::from).toList();
		return new V2RecommendationResponse(items.isEmpty() ? NO_MATCHES : OK, items.size(),
			context, items);
	}

	/**
	 * 목록의 매장 한 곳. 방문 백분위와 관리번호는 순서를 정하는 데만 쓰고 내보내지 않는다.
	 *
	 * @param cellId         매장이 속한 50m 칸 번호. 응답에서는 문자열이다
	 * @param distanceMeters 요청 위치에서 매장까지의 직선거리. 소수 첫째 자리로 반올림한다
	 */
	public record Item(long id, String name, String cellId, StoreKind kind, double lat, double lng,
		double distanceMeters, String googlePlaceId) {

		static Item from(Selected selected) {
			Place place = selected.candidate().place();
			return new Item(place.id(), place.name(), String.valueOf(place.cellId()), place.kind(),
				place.lat(), place.lng(), round(selected.distanceMeters()), place.googlePlaceId());
		}

		private static double round(double meters) {
			return Math.round(meters * 10) / 10.0;
		}
	}
}
