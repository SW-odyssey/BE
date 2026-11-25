package com.odyssey.cellcondition.repository;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.cellcondition.entity.QCellCondition.cellCondition;
import static com.odyssey.legaldong.entity.QLegalDong.legalDong;
import static com.odyssey.store.entity.QStore.store;
import static com.odyssey.store.entity.QStoreNameTranslation.storeNameTranslation;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellcondition.entity.CellQualityStatus;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.store.StoreNames;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AreaRecommendationRepository {

	private final JPAQueryFactory query;

	public record Row(Place place, Area area) {
	}

	public record Page(long total, List<Row> items) {
	}

	/** 지역을 생략하면 서비스 지역 전체에서 조회한다. */
	public Page findPage(String datasetId, Weekday weekday, TimeSlot slot, Area area,
		Set<StoreKind> kinds, double minPercentile, long offset, int size) {
		if (kinds.isEmpty()) {
			return new Page(0, List.of());
		}

		BooleanExpression[] conditions = matchingStoreConditions(datasetId, weekday, slot, area, kinds, minPercentile);
		long total = countMatchingStores(conditions);

		if (offset >= total) {
			return new Page(total, List.of());
		}

		return new Page(total, fetchPageItems(conditions, offset, size));
	}

	private static BooleanExpression[] matchingStoreConditions(String datasetId, Weekday weekday, TimeSlot slot,
		Area area, Set<StoreKind> kinds, double minPercentile) {
		return new BooleanExpression[] {
			matchesVisitCondition(datasetId, weekday, slot, minPercentile),
			isActiveWithGooglePlaceId(),
			store.kind.in(kinds),
			matchesArea(area)
		};
	}

	private static BooleanExpression matchesVisitCondition(String datasetId, Weekday weekday, TimeSlot slot,
		double minPercentile) {
		return cellCondition.id.datasetId.eq(datasetId)
			.and(cellCondition.id.weekday.eq(ApiEnums.toValue(weekday)))
			.and(cellCondition.id.slot.eq(ApiEnums.toValue(slot)))
			.and(cellCondition.qualityStatus.eq(CellQualityStatus.AVAILABLE))
			.and(cellCondition.visitorPercentile.goe(minPercentile));
	}

	private static BooleanExpression isActiveWithGooglePlaceId() {
		return store.active.isTrue()
			.and(store.googlePlaceId.isNotNull())
			.and(store.googlePlaceId.trim().ne(""));
	}

	private static BooleanExpression matchesArea(Area area) {
		return area == null ? legalDong.area.in(Area.values()) : legalDong.area.eq(area);
	}

	private long countMatchingStores(BooleanExpression[] conditions) {
		return queryMatchingStores(query.select(store.id.count()), conditions).fetchOne();
	}

	private List<Row> fetchPageItems(BooleanExpression[] conditions, long offset, int size) {
		JPAQuery<Tuple> stores = queryMatchingStores(selectPageColumns(), conditions);
		List<Tuple> tuples = joinCurrentTranslations(stores)
			.orderBy(cellCondition.visitorPercentile.desc(), store.id.asc())
			.offset(offset)
			.limit(size)
			.fetch();

		return toRows(tuples);
	}

	private JPAQuery<Tuple> selectPageColumns() {
		return query.select(store.id, store.name, cell.cellId, store.kind, store.lat, store.lng, store.sinceDate,
			store.googlePlaceId, legalDong.area, storeNameTranslation.nameEn, storeNameTranslation.nameZh,
			storeNameTranslation.nameJa);
	}

	private static JPAQuery<Tuple> joinCurrentTranslations(JPAQuery<Tuple> stores) {
		return stores.leftJoin(storeNameTranslation)
			.on(storeNameTranslation.storeId.eq(store.id), storeNameTranslation.sourceName.eq(store.name));
	}

	private <T> JPAQuery<T> queryMatchingStores(JPAQuery<T> selection, BooleanExpression[] conditions) {
		return selection.from(store)
			.join(store.cell, cell)
			.join(cell.legalDong, legalDong)
			.join(cellCondition).on(cellCondition.id.cellId.eq(cell.cellId))
			.where(conditions);
	}

	private static List<Row> toRows(List<Tuple> tuples) {
		return tuples.stream()
			.map(AreaRecommendationRepository::toRow)
			.toList();
	}

	private static Row toRow(Tuple row) {
		StoreNames names = new StoreNames(row.get(storeNameTranslation.nameEn),
			row.get(storeNameTranslation.nameZh), row.get(storeNameTranslation.nameJa));
		Place place = new Place(row.get(store.id), row.get(store.name), row.get(cell.cellId),
			row.get(store.kind), row.get(store.lat), row.get(store.lng), row.get(store.sinceDate),
			row.get(store.googlePlaceId), names);
		return new Row(place, row.get(legalDong.area));
	}
}
