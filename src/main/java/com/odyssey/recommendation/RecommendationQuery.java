package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

import java.time.LocalDate;

/**
 * 추천 조회 조건. 컨트롤러가 요청 문자열을 검증 · 변환해서 만든다.
 *
 * <p>"전체 · 상관없음" 은 null 이고, null 인 조건은 조회에서 빠진다.
 * 서비스는 "all" 같은 문자열을 몰라도 되도록 여기서 끊는다.
 *
 * @param area         지역. null 이면 전체
 * @param category     카테고리. null 이면 전체
 * @param foodType     음식점 종류. 음식점이 아니거나 상관없음이면 null
 * @param openedBefore 이 날짜까지 개업한 곳만 (노포, 당일 포함). null 이면 상관없음
 * @param visitDate    방문일. null 이 아니다 (생략하면 컨트롤러가 오늘로 채운다)
 * @param timeSlot     시간대. null 이 아니다 (생략하면 오후)
 * @param densityLevel 붐빔. null 이면 상관없음
 * @param query        이름 검색어. null 이면 검색 안 함
 */
public record RecommendationQuery(
	Area area,
	Category category,
	FoodType foodType,
	LocalDate openedBefore,
	LocalDate visitDate,
	TimeSlot timeSlot,
	DensityLevel densityLevel,
	String query) {
}
