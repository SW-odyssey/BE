package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;
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

	private RecommendationQuery query(LocalDate date, Double lat, Double lng) {
		return new RecommendationQuery(null, null, null, null, date,
			TimeSlot.AFTERNOON, null, null, lat, lng);
	}

	private StoreCardRow store(Long id, Long cell, double lat) {
		return new StoreCardRow(id, "가게", StoreKind.KOREAN, cell, Area.INSADONG,
			lat, 127, LocalDate.of(1990, 1, 1), "place-" + id);
	}
}
