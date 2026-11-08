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

@Repository
@RequiredArgsConstructor
public class StoreRepository {
	private final JPAQueryFactory query;

	/** 후보 칸 안 활성 매장을 ID순으로 반환한다. 추천 순서는 서비스가 정한다. */
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

	private BooleanExpression categoryCondition(Category category) {
		if (category == null) {
			return null;
		}
		return category == Category.CAFE ? store.kind.eq(StoreKind.CAFE)
			: store.kind.ne(StoreKind.CAFE);
	}

	private BooleanExpression foodCondition(Category category, FoodType type) {
		return category != Category.FOOD || type == null ? null
			: store.kind.eq(StoreKind.valueOf(type.name()));
	}

	private BooleanExpression oldStoreCondition(LocalDate cutoff) {
		return cutoff == null ? null : store.sinceDate.loe(cutoff).or(
			JPAExpressions.selectOne().from(storeBadgeEntry)
				.where(storeBadgeEntry.id.storeId.eq(store.id)).exists());
	}
}
