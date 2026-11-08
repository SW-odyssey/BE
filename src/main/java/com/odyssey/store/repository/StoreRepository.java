package com.odyssey.store.repository;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.legaldong.entity.QLegalDong.legalDong;
import static com.odyssey.store.entity.QStore.store;
import static com.odyssey.store.entity.QStoreBadgeEntry.storeBadgeEntry;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.odyssey.store.entity.*;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
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
public class StoreRepository {
	private final JPAQueryFactory query;

	/**
	 * 후보 칸 안의 영업 중이며 place ID가 있는 매장을 카드에 필요한 열만 읽는다.
	 *
	 * @param ids          지역 · 붐빔 조건을 통과한 후보 칸. 주변 칸으로 확장하지 않는다
	 * @param category     카테고리. null 이면 전체
	 * @param foodType     음식점 종류. null 이면 상관없음
	 * @param openedBefore 방문일 기준 30년 전 날짜(당일 포함).
	 *                     공식 배지가 있으면 날짜와 무관하게 포함하며, null 이면 조건을 걸지 않는다
	 * @param text         이름 검색어 (대소문자 무시, 부분 일치). null 이거나 비어 있으면 검색 안 함
	 * @return 매장 카드 행, ID순. 추천 순서는 서비스가 정한다
	 */
	public List<StoreCardRow> findCards(Collection<Long> ids, Category category,
		FoodType foodType, LocalDate openedBefore, String text) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return query.select(Projections.constructor(StoreCardRow.class,
				store.id, store.name, store.kind, cell.cellId, legalDong.area,
				store.lat, store.lng, store.sinceDate, store.googlePlaceId))
			.from(store).join(store.cell, cell).join(cell.legalDong, legalDong)
			.where(cell.cellId.in(ids), store.active.isTrue(), store.googlePlaceId.isNotNull(),
				store.googlePlaceId.ne(""),
				categoryCondition(category), foodCondition(category, foodType),
				oldStoreCondition(openedBefore),
				text == null || text.isBlank() ? null : store.name.containsIgnoreCase(text.strip()))
			.orderBy(store.id.asc()).fetch();
	}

	/** 단일 kind에서 카페와 음식점을 구분한다. 전체 선택이면 조건을 생략한다. */
	private BooleanExpression categoryCondition(Category category) {
		if (category == null) {
			return null;
		}
		return category == Category.CAFE ? store.kind.eq(StoreKind.CAFE)
			: store.kind.ne(StoreKind.CAFE);
	}

	/** 음식점 하위 선택을 같은 이름의 kind로 바꾼다. 음식점이 아니면 조건을 생략한다. */
	private BooleanExpression foodCondition(Category category, FoodType type) {
		return category != Category.FOOD || type == null ? null
			: store.kind.eq(StoreKind.valueOf(type.name()));
	}

	/**
	 * 기준일 이전 개업 또는 공식 배지 보유 여부를 검사한다.
	 * 배지를 조인하지 않고 exists로 확인하여 여러 배지를 가진 매장도 한 번만 반환한다.
	 */
	private BooleanExpression oldStoreCondition(LocalDate cutoff) {
		return cutoff == null ? null : store.sinceDate.loe(cutoff).or(
			JPAExpressions.selectOne().from(storeBadgeEntry)
				.where(storeBadgeEntry.id.storeId.eq(store.id)).exists());
	}
}
