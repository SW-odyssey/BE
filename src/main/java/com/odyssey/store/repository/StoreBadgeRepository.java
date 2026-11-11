package com.odyssey.store.repository;

import static com.odyssey.store.entity.QStoreBadgeEntry.storeBadgeEntry;

import java.util.Collection;
import java.util.List;
import com.odyssey.store.entity.StoreBadgeId;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StoreBadgeRepository {
	private final JPAQueryFactory query;

	/** 선택된 매장들의 배지를 한 번에 읽어 N+1 조회를 피한다. */
	public List<StoreBadgeId> findByStoreIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return query.select(storeBadgeEntry.id).from(storeBadgeEntry)
			.where(storeBadgeEntry.id.storeId.in(ids))
			.orderBy(storeBadgeEntry.id.storeId.asc(), storeBadgeEntry.id.badge.asc()).fetch();
	}
}
