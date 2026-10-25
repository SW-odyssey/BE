package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellslotscore.repository.CellSlotScoreRepository;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.odyssey.store.repository.StoreCardRow;
import com.odyssey.store.repository.StoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * {@link RecommendationService} 의 순서 · 분기를 리포지토리 없이 확인한다.
 *
 * <p>리포지토리는 Mockito 가짜 객체다. given 으로 정한 값을 돌려주고, 정하지 않은 메서드는 빈 리스트를 준다.
 * 리포지토리 쿼리가 맞는지는 각 리포지토리 테스트가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

	// 인사동 칸과 그 동쪽으로 두 칸 떨어진 칸. 두 칸의 3×3 은 가운데 한 줄만 겹친다
	private static final long CELL_A = 81_383_966L;
	private static final long CELL_B = CELL_A + 15999L * 2;

	// 2026-10-26 은 월요일 → 평일 점수를 쓴다
	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
	private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 31);

	@Mock
	private CellSlotScoreRepository cellSlotScoreRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private CellRepository cellRepository;

	// 위 세 가짜 리포지토리를 생성자로 넣어 진짜 서비스를 만든다
	@InjectMocks
	private RecommendationService recommendationService;

	// 가짜 객체에 넘어간 인자를 꺼내 보는 도구
	@Captor
	private ArgumentCaptor<Collection<Long>> cellIdsCaptor;

	@Test
	@DisplayName("순위가 높은 칸의 가게는 id 가 커도 앞에 온다")
	void rankedCellFirst() {
		// given: A 가 1위, B 가 2위. 리포지토리는 가게를 id 순으로 준다 (B 의 1번 → A 의 2번)
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A, CELL_B));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of(store(1L, CELL_B), store(2L, CELL_A)));

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: 받은 순서와 반대로, 1위 칸 A 의 2번이 앞에 온다.
		// 입력과 기대 순서가 다르게 둬야 정렬 코드를 지웠을 때 이 테스트가 깨진다
		assertThat(response.items())
			.extracting(RecommendationResponse.Item::id)
			.containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("같은 칸의 가게끼리는 id 순이다")
	void sameCellById() {
		// given: 한 칸 안의 가게를 id 역순으로 준다
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of(store(3L, CELL_A), store(1L, CELL_A)));

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: thenComparing(id) 를 지우면 받은 순서 [3, 1] 그대로 나와서 깨진다
		assertThat(response.items())
			.extracting(RecommendationResponse.Item::id)
			.containsExactly(1L, 3L);
	}

	@Test
	@DisplayName("두 칸의 3×3 이 겹치면 겹친 칸은 앞 순위 칸의 순서를 따른다")
	void overlapKeepsHigherRank() {
		// given: A(1위)와 그 서쪽으로 두 칸 떨어진 west(2위). 겹치는 줄은 A 의 바로 서쪽 줄이다.
		// A 를 넓힐 때 overlap 은 1번, west 는 2위 칸을 넓힐 때 13번이 된다.
		// put 으로 덮어쓰면 overlap 이 west 를 넓힐 때 마지막에 다시 들어가 15번이 되어 west 보다 뒤로 간다
		long west = CELL_A - 15999L * 2;
		long overlap = CELL_A - 15999L;
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A, west));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of(store(1L, west), store(2L, overlap)));

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: overlap 칸의 가게가 west 칸의 가게보다 앞에 온다
		assertThat(response.items())
			.extracting(RecommendationResponse.Item::id)
			.containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("방문일이 토요일이면 주말 점수로 칸을 찾는다")
	void weekend() {
		// given: 칸 조회는 기본값(빈 리스트)을 돌려준다. 어떤 인자로 불렸는지만 본다

		// when
		recommendationService.recommend(query(SATURDAY));

		// then: 응답의 dayType 이 아니라 실제 조회에 넘긴 값을 본다. 이 값이 틀리면 엉뚱한 점수를 쓴다
		then(cellSlotScoreRepository).should()
			.findRankedCellIds(eq(DayType.WEEKEND), eq(TimeSlot.AFTERNOON), any(), any());
	}

	@Test
	@DisplayName("추천 칸이 없으면 가게를 조회하지 않고 빈 결과를 준다")
	void noRankedCells() {
		// given: 지역 · 붐빔 조건에 맞는 칸이 하나도 없다
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of());

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: 빈 칸 목록으로 가게를 조회하지 않는다
		assertThat(response.total()).isZero();
		then(storeRepository).should(never()).findCards(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("가게가 0곳이면 칸을 조회하지 않고 빈 결과를 준다")
	void noStores() {
		// given: 추천 칸은 있지만 그 안에 조건에 맞는 가게가 없다
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of());

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: 응답만 보면 early return 이 없어도 똑같이 0곳이다 (가짜 cellRepository 도 빈 리스트를 주므로).
		// 그래서 빈 in () 조회를 하지 않았는지는 호출 기록으로 확인한다
		assertThat(response.total()).isZero();
		then(cellRepository).should(never()).findByCellIdInOrderByCellId(any());
	}

	@Test
	@DisplayName("가게가 200곳이면 그대로 준다")
	void maxResults() {
		// given: 최대 개수와 딱 같은 수
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(stores(200, CELL_A));

		// when
		RecommendationResponse response = recommendationService.recommend(query(MONDAY));

		// then: "넘으면" 오류라서 200곳은 통과한다 (> 를 >= 로 바꾸면 깨진다)
		assertThat(response.total()).isEqualTo(200);
	}

	@Test
	@DisplayName("가게가 200곳을 넘으면 범위를 좁히라는 오류를 낸다")
	void tooManyStores() {
		// given
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(stores(201, CELL_A));

		// when · then: 잘라서 주지 않고 오류를 낸다. FE 는 이 코드를 보고 "조건을 좁혀 주세요" 를 띄운다 (#28)
		assertThatThrownBy(() -> recommendationService.recommend(query(MONDAY)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorType())
			.isEqualTo(CommonErrorCode.QUERY_SCOPE_TOO_WIDE);
	}

	@Test
	@DisplayName("지도용 칸은 넓힌 9칸이 아니라 결과 가게가 있는 칸만 한 번씩 조회한다")
	void cellsOfResultsOnly() {
		// given: A 를 9칸으로 넓혔지만 가게는 A 에 2곳, A 의 북쪽 칸에 1곳뿐이다
		long north = CELL_A + 1;
		given(cellSlotScoreRepository.findRankedCellIds(any(), any(), any(), any()))
			.willReturn(List.of(CELL_A));

		given(storeRepository.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of(store(1L, CELL_A), store(2L, CELL_A), store(3L, north)));

		// when
		recommendationService.recommend(query(MONDAY));

		// then: 9칸 전체가 아니라 2칸만, A 도 한 번만 넘긴다. 순서는 리포지토리가 정하므로 보지 않는다
		then(cellRepository).should().findByCellIdInOrderByCellId(cellIdsCaptor.capture());
		assertThat(cellIdsCaptor.getValue()).containsExactlyInAnyOrder(CELL_A, north);
	}

	// 조건 없이 방문일 · 시간대만 정한 조회. null 은 "전체 · 상관없음"
	private static RecommendationQuery query(LocalDate visitDate) {
		return new RecommendationQuery(
			null,               // area
			null,               // category
			null,               // foodType
			null,               // openedBefore
			visitDate,
			TimeSlot.AFTERNOON,
			null,               // densityLevel
			null);              // query
	}

	// 순서 확인에는 id 와 칸만 쓴다. 나머지 열은 아무 값이나 채운다
	private static StoreCardRow store(long id, long cellId) {
		return new StoreCardRow(
			id,
			"가게" + id,
			Category.FOOD,
			FoodType.KOREAN,
			cellId,
			Area.INSADONG,
			37.57,
			126.98,
			LocalDate.of(1990, 1, 1),
			null);              // googlePlaceId
	}

	// 한 칸 안의 가게 count 곳. id 는 1 부터
	private static List<StoreCardRow> stores(int count, long cellId) {
		return LongStream.rangeClosed(1, count)
			.mapToObj(id -> store(id, cellId))
			.toList();
	}
}
