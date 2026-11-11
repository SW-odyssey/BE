package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.odyssey.cell.entity.Area;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cellslotscore.entity.*;
import com.odyssey.cellslotscore.repository.*;
import com.odyssey.store.entity.*;
import com.odyssey.store.repository.*;

/**
 * 추천 서비스의 순서 · 분기를 리포지토리 없이 확인한다.
 *
 * <p>리포지토리는 Mockito 가짜 객체다. given 으로 정한 값을 돌려주고,
 * 정하지 않은 메서드는 빈 리스트를 준다. 실제 쿼리는 각 리포지토리 테스트가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
	@Mock CellSlotScoreRepository scores;
	@Mock StoreRepository stores;
	@Mock CellRepository cells;
	@Mock StoreBadgeRepository badges;
	@InjectMocks RecommendationService service;

	@ParameterizedTest
	@CsvSource({"2026-10-29,10,MON_THU", "2026-10-30,10,FRI",
		"2026-10-31,10,SAT", "2026-11-01,11,SUN"})
	@DisplayName("방문일의 달과 요일 4분류로 점수를 조회한다")
	void period(LocalDate date, int month, DayType day) {
		// given / when
		service.recommend(query(date, null, null));
		// then
		then(scores).should().findRankedCells(month, day, TimeSlot.AFTERNOON, null, null);
	}

	@Test
	@DisplayName("후보 칸이 없으면 가게 조회 없이 빈 응답이다")
	void emptyCells() {
		// given / when
		var result = service.recommend(query(LocalDate.of(2026, 11, 1), null, null));
		// then
		assertThat(result.items()).isEmpty();
		then(stores).should(never()).findCards(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("구역 밖 위치면 거리 대신 점수순으로 고르고 선택된 매장의 배지만 읽는다")
	void outsideArea() {
		// given
		given(scores.findRankedCells(anyInt(), any(), any(), any(), any()))
			.willReturn(List.of(new RankedCell(10L, 100), new RankedCell(20L, 10)));
		given(stores.findCards(any(), any(), any(), any(), any()))
			.willReturn(List.of(store(1L, 20L, 37), store(2L, 10L, 38)));
		given(cells.containsPoint(37, 127)).willReturn(false);
		given(badges.findByStoreIds(List.of(2L, 1L)))
			.willReturn(List.of(new StoreBadgeId(2L, StoreBadge.ORAE_GAGE)));
		// when
		var result = service.recommend(query(LocalDate.of(2026, 11, 1), 37.0, 127.0));
		// then
		assertThat(result.items()).extracting(RecommendationResponse.Item::id).containsExactly(2L, 1L);
		assertThat(result.items().getFirst().badges()).containsExactly(StoreBadge.ORAE_GAGE);
		then(stores).should().findCards(List.of(10L, 20L), null, null, null, null);
	}

	@ParameterizedTest
	@CsvSource({"0,30,true", "1,30,true", "2,1,false", "3,0,false", "2147483647,0,false"})
	@DisplayName("현재 페이지의 장소와 배지만 반환하고 마지막 이후에는 빈 응답이다")
	void pageStoresAndBadges(int page, int count, boolean hasNext) {
		// given
		given(scores.findRankedCells(anyInt(), any(), any(), any(), any()))
			.willReturn(List.of(new RankedCell(10L, 100)));
		given(stores.findCards(any(), any(), any(), any(), any()))
			.willReturn(LongStream.rangeClosed(1, 61)
				.mapToObj(i -> store(62 - i, 10L, 37)).toList());
		long firstId = (long) page * 30 + 1;
		var expectedIds = LongStream.range(firstId, firstId + count).boxed().toList();

		// when
		var result = service.recommend(query(LocalDate.of(2026, 11, 1), null, null, page));

		// then
		assertThat(result.page()).isEqualTo(page);
		assertThat(result.total()).isEqualTo(count);
		assertThat(result.hasNext()).isEqualTo(hasNext);
		assertThat(result.items()).extracting(RecommendationResponse.Item::id)
			.containsExactlyElementsOf(expectedIds);
		if (count == 0) {
			then(badges).shouldHaveNoInteractions();
		} else {
			then(badges).should().findByStoreIds(expectedIds);
		}
	}

	@Test
	@DisplayName("후보 칸이 없는 다음 페이지도 요청한 페이지 번호를 유지한다")
	void emptyLaterPage() {
		// given
		var request = query(LocalDate.of(2026, 11, 1), null, null, 4);

		// when
		var result = service.recommend(request);

		// then
		assertThat(result.page()).isEqualTo(4);
		assertThat(result.total()).isZero();
		assertThat(result.items()).isEmpty();
		assertThat(result.hasNext()).isFalse();
		then(stores).shouldHaveNoInteractions();
		then(badges).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("후보 칸에 매장이 없으면 위치와 배지를 조회하지 않는다")
	void emptyStoresOnLaterPage() {
		// given
		given(scores.findRankedCells(anyInt(), any(), any(), any(), any()))
			.willReturn(List.of(new RankedCell(10L, 100)));

		// when
		var result = service.recommend(query(LocalDate.of(2026, 11, 1), 37.0, 127.0, 2));

		// then
		assertThat(result.page()).isEqualTo(2);
		assertThat(result.items()).isEmpty();
		assertThat(result.hasNext()).isFalse();
		then(cells).shouldHaveNoInteractions();
		then(badges).shouldHaveNoInteractions();
	}

	private RecommendationQuery query(LocalDate date, Double lat, Double lng) {
		return query(date, lat, lng, 0);
	}

	private RecommendationQuery query(LocalDate date, Double lat, Double lng, int page) {
		return new RecommendationQuery(null, null, null, null, date,
			TimeSlot.AFTERNOON, null, null, lat, lng, page);
	}

	private StoreCardRow store(Long id, Long cell, double lat) {
		return new StoreCardRow(id, "가게", StoreKind.KOREAN, cell, Area.INSADONG,
			lat, 127, LocalDate.of(1990, 1, 1), "place-" + id);
	}
}
