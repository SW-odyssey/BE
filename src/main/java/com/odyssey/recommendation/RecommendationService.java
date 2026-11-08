package com.odyssey.recommendation;

import java.util.*;
import java.util.stream.Collectors;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.repository.CellSlotScoreRepository;
import com.odyssey.store.entity.StoreBadge;
import com.odyssey.store.entity.StoreBadgeId;
import com.odyssey.store.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {
	private final CellSlotScoreRepository scores;
	private final StoreRepository stores;
	private final CellRepository cells;
	private final StoreBadgeRepository badges;

	public RecommendationResponse recommend(RecommendationQuery request) {
		DayType day = DayType.from(request.visitDate());
		var ranked = scores.findRankedCells(request.visitDate().getMonthValue(), day,
			request.timeSlot(), request.area(), request.densityLevel());
		if (ranked.isEmpty()) {
			return RecommendationResponse.empty(request.visitDate(), day, request.timeSlot());
		}
		var candidates = stores.findCards(ranked.stream().map(r -> r.cellId()).toList(),
			request.category(), request.foodType(), request.openedBefore(), request.query());
		if (candidates.isEmpty()) {
			return RecommendationResponse.empty(request.visitDate(), day, request.timeSlot());
		}
		boolean useDistance = request.lat() != null && request.lng() != null
			&& cells.containsPoint(request.lat(), request.lng());
		var selected = RecommendationSelector.select(
			ranked, candidates, useDistance ? request.lat() : null,
			useDistance ? request.lng() : null);
		Map<Long, List<StoreBadge>> byStore = badges.findByStoreIds(
			selected.stream().map(StoreCardRow::id).toList()).stream()
			.collect(Collectors.groupingBy(StoreBadgeId::getStoreId,
				Collectors.mapping(StoreBadgeId::getBadge, Collectors.toList())));
		return RecommendationResponse.of(
			request.visitDate(), day, request.timeSlot(), selected, byStore);
	}
}
