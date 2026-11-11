package com.odyssey.recommendation;

import java.time.LocalDate;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

/**
 * 추천 조회 조건. 컨트롤러가 요청 문자열을 검증 · 변환해서 만든다.
 *
 * <p>"전체 · 상관없음" 은 null 이고, null 인 조건은 조회에서 빠진다.
 * 서비스는 "all" 같은 문자열을 몰라도 되도록 여기서 끊는다.
 *
 * @param area         지역. null 이면 전체
 * @param category     카테고리. null 이면 전체
 * @param foodType     음식점 종류. 음식점이 아니거나 상관없음이면 null
 * @param openedBefore 방문일에서 30년을 뺀 날짜(당일 포함). null 이면 노포 필터 해제.
 *                     공식 배지가 있으면 이 날짜보다 늦게 개업했어도 노포로 포함한다
 * @param visitDate    방문일. null 이 아니다 (생략하면 컨트롤러가 서울 기준 오늘로 채운다)
 * @param timeSlot     시간대. null 이 아니다 (생략하면 오후)
 * @param densityLevel 붐빔 선택. null 이면 상관없음, LIVELY는 peakHour 조건으로 바꿔 조회한다
 * @param query        이름 검색어. null 이면 검색 안 함
 * @param lat          사용자 위도(WGS84, 도). lng와 함께 있거나 둘 다 null이다
 * @param lng          사용자 경도(WGS84, 도). 서비스 칸 밖이면 위치 기반 정렬을 쓰지 않는다
 * @param page         0부터 시작하는 페이지 번호. 컨트롤러에서 음수를 거절한다
 */
public record RecommendationQuery(
	Area area, Category category, FoodType foodType, LocalDate openedBefore,
	LocalDate visitDate, TimeSlot timeSlot, DensityLevel densityLevel,
	String query, Double lat, Double lng, int page) {
}
