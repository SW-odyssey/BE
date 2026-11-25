package com.odyssey.recommendation.v2;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.odyssey.cell.entity.Area;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.StoreNames;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

import io.swagger.v3.oas.annotations.media.Schema;

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

	V2RecommendationResponse withLanguage(Language language) {
		return new V2RecommendationResponse(status, total, context, items.stream()
			.map(item -> item.withLanguage(language)).toList());
	}

	/**
	 * 목록의 매장 한 곳. 방문 백분위와 관리번호는 순서를 정하는 데만 쓰고 내보내지 않는다.
	 *
	 * @param cellId         매장이 속한 50m 칸 번호. 응답에서는 문자열이다
	 * @param distanceMeters 요청 위치에서 매장까지의 직선거리. 소수 첫째 자리로 반올림한다
	 * @param areaId         메타의 areas[].id 와 같은 지역 ID. 모든 칸이 법정동을 거쳐 지역이 정해져서 항상 있다
	 */
	public record Item(long id, String name, String cellId, StoreKind kind, double lat, double lng,
		double distanceMeters, String googlePlaceId, Area areaId,
		@JsonIgnore @Schema(hidden = true) StoreNames names) {

		public Item(long id, String name, String cellId, StoreKind kind, double lat, double lng,
			double distanceMeters, String googlePlaceId, Area areaId) {
			this(id, name, cellId, kind, lat, lng, distanceMeters, googlePlaceId, areaId, StoreNames.EMPTY);
		}

		Item withLanguage(Language language) {
			return new Item(id, names.display(name, language), cellId, kind, lat, lng, distanceMeters,
				googlePlaceId, areaId);
		}

		static Item from(Selected selected) {
			Place place = selected.candidate().place();
			return new Item(place.id(), place.name(), String.valueOf(place.cellId()), place.kind(),
				place.lat(), place.lng(), round(selected.distanceMeters()), place.googlePlaceId(), place.area(),
				place.names());
		}

		private static double round(double meters) {
			return Math.round(meters * 10) / 10.0;
		}
	}
}
