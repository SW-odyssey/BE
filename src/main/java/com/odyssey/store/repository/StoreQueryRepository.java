package com.odyssey.store.repository;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.store.entity.QStore.store;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 조건이 바뀌는 매장 조회 (QueryDSL).
 *
 * <p>where 에 null 을 넘기면 그 조건이 SQL 에서 빠진다. 그래서 "전체 · 상관없음" 은
 * 조건 메서드가 null 을 돌려주는 것으로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class StoreQueryRepository {

	private final JPAQueryFactory query;

	/**
	 * 칸 목록 안의 영업 중 · 비프랜차이즈 매장을 카드에 필요한 열만 읽는다 (#30 추천).
	 *
	 * @param cellIds      찾을 칸 (추천 칸과 주변 3×3)
	 * @param category     카테고리. null 이면 전체
	 * @param foodType     음식점 종류. null 이면 상관없음
	 * @param openedBefore 이 날짜 이전에 개업한 곳만 (노포). null 이면 상관없음
	 * @param q            이름 검색어 (대소문자 무시, 부분 일치). null 이거나 비어 있으면 검색 안 함
	 * @return 매장 카드 행, id 순
	 */
	public List<StoreCardRow> findCards(
		Collection<Long> cellIds,
		Category category,
		FoodType foodType,
		LocalDate openedBefore,
		String q) {

		return query
			.select(Projections.constructor(StoreCardRow.class,
				store.id, store.name, store.category, store.foodType,
				cell.cellId, cell.area, store.lat, store.lng, store.licenseDate))
			.from(store)
			.join(store.cell, cell)
			.where(
				cell.cellId.in(cellIds),
				store.active.isTrue(),
				store.franchise.isFalse(),
				categoryEq(category),
				foodTypeEq(foodType),
				openedBefore(openedBefore),
				nameContains(q))
			.orderBy(store.id.asc())
			.fetch();
	}

	private static BooleanExpression categoryEq(Category category) {
		return category == null ? null : store.category.eq(category);
	}

	private static BooleanExpression foodTypeEq(FoodType foodType) {
		return foodType == null ? null : store.foodType.eq(foodType);
	}

	private static BooleanExpression openedBefore(LocalDate date) {
		return date == null ? null : store.licenseDate.loe(date);
	}

	private static BooleanExpression nameContains(String q) {
		return q == null || q.isBlank() ? null : store.name.containsIgnoreCase(q.strip());
	}
}
