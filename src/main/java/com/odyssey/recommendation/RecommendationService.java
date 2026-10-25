package com.odyssey.recommendation;

import com.odyssey.cell.CellGrid;
import com.odyssey.cell.entity.Cell;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.repository.CellSlotScoreRepository;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.repository.StoreCardRow;
import com.odyssey.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추천 가게를 고른다 (GitHub 이슈 #30 "서버가 고르는 방법").
 *
 * <p>시간대 점수(U+ 인구)는 가게가 아니라 50m 칸에만 있다. 그래서 칸을 먼저 고르고 그 주변 가게를 보여 준다.
 *
 * <ol>
 *   <li>방문일 → 평일 / 주말</li>
 *   <li>그 요일 · 시간대에 추천 대상인 칸을 순위대로 (cell_slot_score)</li>
 *   <li>각 칸을 주변 3×3 으로 넓히고, 칸마다 순서 번호를 붙인다</li>
 *   <li>넓힌 칸 안에서 조건에 맞는 가게를 한 번에 조회</li>
 *   <li>200곳 넘으면 오류, 아니면 칸 순서 → 가게 id 순으로 정렬</li>
 *   <li>결과 가게가 있는 칸만 지도용으로 조회해서 응답</li>
 * </ol>
 *
 * <p>2단계에서 칸을 몇 개까지 쓸지 아직 정하지 않았다 (#30 댓글).
 * 지금은 전부 써서 지역 전체 기본값이면 200곳을 넘는다.
 */
@Service
@RequiredArgsConstructor
// 메서드 하나 = 트랜잭션 하나. 세 조회가 같은 커넥션에서 실행된다.
// open-in-view 가 꺼져 있어서 메서드가 끝나면 LAZY 로딩이 안 된다 → 응답은 이 안에서 다 만든다
@Transactional(readOnly = true)
public class RecommendationService {

	/** 한 번에 내려 주는 최대 가게 수 (#28). 넘으면 잘라 주지 않고 오류를 낸다 */
	private static final int MAX_RESULTS = 200;

	private final CellSlotScoreRepository cellSlotScoreRepository;
	private final StoreRepository storeRepository;
	private final CellRepository cellRepository;

	public RecommendationResponse recommend(RecommendationQuery recommendationQuery) {
		// 1. 방문일 → 평일 / 주말
		DayType dayType = toDayType(recommendationQuery.visitDate());

		// 2. 그 요일 · 시간대에 추천 대상인 칸을 순위대로. 지역 · 붐빔 조건은 여기서 걸린다
		List<Long> rankedCellIds = cellSlotScoreRepository.findRankedCellIds(
			dayType,
			recommendationQuery.timeSlot(),
			recommendationQuery.area(),
			recommendationQuery.densityLevel());

		// 3. 주변 3×3 으로 넓히고 칸마다 순서 번호
		Map<Long, Integer> orderMap = expandToNeighbors(rankedCellIds);

		// 조건에 맞는 칸이 없으면 오류가 아니라 빈 결과 (#30)
		if (orderMap.isEmpty()) {
			return RecommendationResponse.empty(
				recommendationQuery.visitDate(), dayType, recommendationQuery.timeSlot());
		}

		// 4. 넓힌 칸 안에서 조건에 맞는 가게
		List<StoreCardRow> stores = findStores(recommendationQuery, orderMap);

		// 가게가 0곳이면 6단계에서 빈 목록으로 in () 조회를 하게 되므로 여기서 끝낸다
		if (stores.isEmpty()) {
			return RecommendationResponse.empty(
				recommendationQuery.visitDate(), dayType, recommendationQuery.timeSlot());
		}

		// 5. 200곳 확인 후 칸 순서대로 정렬
		validateSize(stores);
		List<StoreCardRow> sorted = sortByCellOrder(stores, orderMap);

		// 6. 결과 가게가 있는 칸만 지도용으로
		List<Cell> cells = findCellsOf(sorted);
		return RecommendationResponse.of(
			recommendationQuery.visitDate(), dayType, recommendationQuery.timeSlot(), sorted, cells);
	}

	/**
	 * 점수가 평일 / 주말로 나뉘어 있어서 날짜를 먼저 요일 묶음으로 바꾼다.
	 *
	 * <p>토 · 일만 주말이다. 공휴일은 지금 평일로 계산된다 (공휴일 처리는 미정)
	 */
	private static DayType toDayType(LocalDate visitDate) {
		DayOfWeek day = visitDate.getDayOfWeek();
		return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? DayType.WEEKEND : DayType.WEEKDAY;
	}

	/**
	 * 순위대로 받은 칸을 각각 주변 3×3 으로 넓히고, 넓힌 칸마다 순서 번호를 붙인다.
	 *
	 * <p>칸 하나에는 가게가 적어서 (중앙값 1곳, 622칸 중 265칸은 0곳) 주변 8칸까지 넓힌다.
	 * 돌려주는 맵 하나가 두 가지 일을 한다.
	 * <ul>
	 *   <li>keySet() → 4단계에서 가게를 조회할 칸 목록</li>
	 *   <li>get(cellId) → 5단계 정렬 기준. 작을수록 앞</li>
	 * </ul>
	 *
	 * <p>예) 칸A(1위) 9칸 → 0~8번, 칸B(2위) 9칸 중 A 와 겹치는 6칸은 건너뛰고 새 3칸만 9~11번
	 */
	private static Map<Long, Integer> expandToNeighbors(List<Long> rankedCellIds) {
		Map<Long, Integer> cellOrder = new LinkedHashMap<>();
		for (Long rankedCellId : rankedCellIds) {
			for (Long id : CellGrid.neighbors3x3(rankedCellId)) {
				// put 이 아니라 putIfAbsent: 겹치는 칸은 순위가 더 높은 칸의 번호를 유지해야 한다
				cellOrder.putIfAbsent(id, cellOrder.size());
			}
		}
		return cellOrder;
	}

	/**
	 * 넓힌 칸 전체를 쿼리 한 번으로 조회한다. 칸이 겹쳐도 같은 가게가 두 번 나오지 않는다 (#30 중복 제거).
	 *
	 * <p>결과는 가게 id 순이라 칸 순위는 아직 반영되지 않았다. 순위는 {@link #sortByCellOrder} 가 맞춘다.
	 */
	private List<StoreCardRow> findStores(
		RecommendationQuery recommendationQuery, Map<Long, Integer> orderMap) {
		return storeRepository.findCards(
			orderMap.keySet(),
			recommendationQuery.category(),
			recommendationQuery.foodType(),
			recommendationQuery.openedBefore(),
			recommendationQuery.query());
	}

	/** 200곳을 넘으면 FE 가 "지역이나 조건을 좁혀 주세요" 를 띄운다 (#28) */
	private static void validateSize(List<StoreCardRow> stores) {
		if (stores.size() > MAX_RESULTS) {
			throw new BusinessException(CommonErrorCode.QUERY_SCOPE_TOO_WIDE,
				"결과가 " + MAX_RESULTS + "곳을 넘습니다. 지역이나 조건을 좁혀 주세요.");
		}
	}

	/**
	 * 칸 순서 → 같은 칸이면 가게 id 순으로 정렬한다.
	 *
	 * <p>이 순서가 곧 응답 items 의 추천 순서다. 점수 숫자는 내려 주지 않는다.
	 */
	private static List<StoreCardRow> sortByCellOrder(
		List<StoreCardRow> stores, Map<Long, Integer> orderMap) {
		return stores.stream()
			.sorted(Comparator.comparing((StoreCardRow store) -> orderMap.get(store.cellId()))
				.thenComparing(StoreCardRow::id))
			.toList();
	}

	/**
	 * 지도에는 넓힌 칸 전체가 아니라 결과 가게가 실제로 있는 칸만 그린다.
	 *
	 * <p>칸 번호 순 정렬은 리포지토리 쿼리(OrderByCellId)가 해서 여기서는 하지 않는다.
	 */
	private List<Cell> findCellsOf(List<StoreCardRow> stores) {
		List<Long> cellIds = stores.stream()
			.map(StoreCardRow::cellId)
			.distinct()
			.toList();
		return cellRepository.findByCellIdInOrderByCellId(cellIds);
	}
}
