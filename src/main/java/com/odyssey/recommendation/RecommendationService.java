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

/**
 * 추천 가게를 고른다 (GitHub 이슈 #43, 기존 #30의 v3 전환).
 *
 * <p>시간대 점수(U+ 인구)는 가게가 아니라 50m 칸에만 있다.
 * 그래서 칸을 먼저 고르고, 그 칸 안에서 조건에 맞는 가게를 보여 준다.
 *
 * <ol>
 *   <li>방문일 → 달과 요일 묶음 4개</li>
 *   <li>그 달 · 요일 · 시간대에 지역과 붐빔 조건에 맞는 칸을 점수순으로 조회</li>
 *   <li>후보 칸 안에서 조건에 맞는 가게를 한 번에 조회</li>
 *   <li>서비스 구역 안 위치가 있으면 500m 안에서 점수 구간 · 거리순으로 정렬 후 페이지 선별</li>
 *   <li>선택한 가게의 배지를 한 번에 조회해서 응답</li>
 * </ol>
 *
 * <p>위치가 없거나 구역 밖이면 점수순으로 고른다.
 * 자세한 구간 확장 규칙은 {@link RecommendationSelector}가 맡는다.
 */
@Service
@RequiredArgsConstructor
// 메서드 하나 = 트랜잭션 하나. 조회가 같은 커넥션에서 실행된다.
// open-in-view 가 꺼져 있어서 메서드가 끝나면 LAZY 로딩이 안 된다 → 응답은 이 안에서 다 만든다
@Transactional(readOnly = true)
public class RecommendationService {
	private final CellSlotScoreRepository scores;
	private final StoreRepository stores;
	private final CellRepository cells;
	private final StoreBadgeRepository badges;

	/**
	 * 검증된 조건으로 후보를 조회하고, 요청한 페이지의 최대 30곳과 해당 배지를 응답으로 만든다.
	 * 후보 칸이나 매장이 없으면 후속 조회 없이 빈 응답을 반환한다.
	 */
	public RecommendationResponse recommend(RecommendationQuery request) {
		// 1. 방문일 → 달과 요일 묶음. 공휴일도 해당 요일 그대로 쓴다.
		DayType day = DayType.from(request.visitDate());
		// 2. 지역 · 붐빔 조건은 칸을 고를 때 걸린다.
		var ranked = scores.findRankedCells(request.visitDate().getMonthValue(), day,
			request.timeSlot(), request.area(), request.densityLevel());
		// 조건에 맞는 칸이 없으면 오류가 아니라 빈 결과 (#30).
		// 빈 칸 목록으로 in () 조회를 하지 않는다.
		if (ranked.isEmpty()) {
			return RecommendationResponse.empty(
				request.visitDate(), day, request.timeSlot(), request.page());
		}
		// 3. 후보 칸 전체를 쿼리 한 번으로 조회한다. 매장은 중복 없이 ID순으로 온다.
		var candidates = stores.findCards(ranked.stream().map(r -> r.cellId()).toList(),
			request.category(), request.foodType(), request.openedBefore(), request.query());
		// 가게가 0곳이면 위치 · 배지 조회를 하지 않고 여기서 끝낸다.
		if (candidates.isEmpty()) {
			return RecommendationResponse.empty(
				request.visitDate(), day, request.timeSlot(), request.page());
		}
		// 4. 받은 ID순이 아니라 추천 순서로 선별한다. 이 순서가 응답 items의 순서다.
		boolean useDistance = request.lat() != null && request.lng() != null
			&& cells.containsPoint(request.lat(), request.lng());
		var ordered = RecommendationSelector.rank(
			ranked, candidates, useDistance ? request.lat() : null,
			useDistance ? request.lng() : null);
		var page = RecommendationPage.from(ordered, request.page());
		var selected = page.stores();
		if (selected.isEmpty()) {
			return RecommendationResponse.empty(
				request.visitDate(), day, request.timeSlot(), request.page());
		}
		// 5. 선택한 가게에 필요한 배지만 한 번에 조회한다.
		Map<Long, List<StoreBadge>> byStore = badges.findByStoreIds(
			selected.stream().map(StoreCardRow::id).toList()).stream()
			.collect(Collectors.groupingBy(StoreBadgeId::getStoreId,
				Collectors.mapping(StoreBadgeId::getBadge, Collectors.toList())));
		return RecommendationResponse.of(
			request.visitDate(), day, request.timeSlot(), page, byStore);
	}
}
