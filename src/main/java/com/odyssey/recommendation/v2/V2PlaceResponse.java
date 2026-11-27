package com.odyssey.recommendation.v2;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.odyssey.cell.entity.Area;
import com.odyssey.store.StoreNames;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소 기본 정보 응답. 필드 아홉 개가 API 계약이고, DB 조회 모델이 바뀌어도 여기서 고른 것만 나간다.
 *
 * <p>kind 는 Jackson 설정으로 소문자가 되고, sinceDate 는 ISO 날짜다. null 인 값도 키는 남긴다.
 *
 * @param id            장소 번호. 목록과 같은 숫자다
 * @param cellId        장소가 속한 50m 칸 번호. 응답에서는 문자열이다
 * @param sinceDate     이력 기준 개업일. 확인할 수 없으면 null
 * @param googlePlaceId 구글 장소 ID. 비활성 매장이 아니어도 적재 중에는 null 일 수 있다
 * @param areaId        메타의 areas[].id 와 같은 지역 ID. 모든 칸이 법정동을 거쳐 지역이 정해져서 항상 있다
 */
public record V2PlaceResponse(long id, String name, String cellId, StoreKind kind, double lat,
	double lng, LocalDate sinceDate, String googlePlaceId, Area areaId,
	@JsonIgnore @Schema(hidden = true) StoreNames names) {

	public V2PlaceResponse(long id, String name, String cellId, StoreKind kind, double lat, double lng,
		LocalDate sinceDate, String googlePlaceId, Area areaId) {
		this(id, name, cellId, kind, lat, lng, sinceDate, googlePlaceId, areaId, StoreNames.EMPTY);
	}

	V2PlaceResponse withLanguage(Language language) {
		return new V2PlaceResponse(id, names.display(name, language), cellId, kind, lat, lng, sinceDate,
			googlePlaceId, areaId);
	}

	static V2PlaceResponse from(Place place) {
		return new V2PlaceResponse(place.id(), place.name(), String.valueOf(place.cellId()),
			place.kind(), place.lat(), place.lng(), place.sinceDate(), place.googlePlaceId(), place.area(), place.names());
	}
}
