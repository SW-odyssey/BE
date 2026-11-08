package com.odyssey.store.repository;

import java.time.LocalDate;
import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.StoreKind;

/**
 * 추천 목록 카드에 필요한 매장 열만 담는 프로젝션. 엔티티 전체를 읽지 않는다.
 *
 * @param kind          API의 category · foodType으로 변환할 단일 가게 분류
 * @param cellId        매장이 속한 50m 칸. 후보 칸 점수와 연결하는 키
 * @param area          매장 칸의 법정동에서 읽은 서비스 지역
 * @param lat           인허가 좌표를 WGS84로 변환한 위도(도)
 * @param lng           인허가 좌표를 WGS84로 변환한 경도(도)
 * @param sinceDate     analysis가 인허가 이력으로 계산한 개업일. 미확정이면 null
 * @param googlePlaceId 외부 장소 상세 조회에 쓸 ID. 추천 조회에서는 없는 매장을 제외한다
 */
public record StoreCardRow(
	Long id, String name, StoreKind kind, Long cellId, Area area,
	double lat, double lng, LocalDate sinceDate, String googlePlaceId) {
}
