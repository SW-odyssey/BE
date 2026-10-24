package com.odyssey.store.repository;

import java.time.LocalDate;

import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

/**
 * 추천 목록 카드에 필요한 매장 열만 담는 프로젝션. 엔티티 전체를 읽지 않는다.
 */
public record StoreCardRow(
	Long id,
	String name,
	Category category,
	FoodType foodType,
	Long cellId,
	Area area,
	double lat,
	double lng,
	LocalDate licenseDate) {
}
