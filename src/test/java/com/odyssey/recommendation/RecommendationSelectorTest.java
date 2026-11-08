package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.repository.RankedCell;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StoreCardRow;

class RecommendationSelectorTest {
	@Test
	@DisplayName("500m 안의 낮은 구간도 밖의 최고점보다 먼저 담는다")
	void radiusBeforeScore() {
		// given
		var cells = List.of(new RankedCell(1L, 100), new RankedCell(2L, 10));
		var stores = List.of(store(1, 1, 600), store(2, 2, 100));
		// when
		var result = RecommendationSelector.select(cells, stores, 0.0, 0.0);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("500m 안에서는 높은 백분위 구간을 먼저, 같은 구간은 가까운 순으로 담는다")
	void percentileThenDistance() {
		// given: 최고 점수 두 칸은 같은 80+ 구간이다.
		var cells = IntStream.rangeClosed(1, 10)
			.mapToObj(i -> new RankedCell((long) i, i)).toList();
		var stores = List.of(store(1, 10, 300), store(2, 9, 200), store(3, 1, 10));
		// when
		var result = RecommendationSelector.select(cells, stores, 0.0, 0.0);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 1L, 3L);
	}

	@Test
	@DisplayName("500m 밖에서는 점수 구간보다 거리순으로 채운다")
	void outsideByDistance() {
		// given
		var cells = List.of(new RankedCell(1L, 100), new RankedCell(2L, 10));
		// when
		var result = RecommendationSelector.select(cells,
			List.of(store(1, 1, 900), store(2, 2, 600)), 0.0, 0.0);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("위치가 없으면 점수 내림차순이며 동점은 매장 ID순이다")
	void noLocation() {
		// given
		var cells = List.of(new RankedCell(1L, 100), new RankedCell(2L, 10));
		// when
		var result = RecommendationSelector.select(cells,
			List.of(store(1, 2, 10), store(3, 1, 10), store(2, 1, 20)), null, null);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 3L, 1L);
	}

	@ParameterizedTest
	@ValueSource(ints = {29, 30, 31, 201})
	@DisplayName("후보가 많아도 오류 없이 최대 30곳까지만 반환한다")
	void limit(int count) {
		// given
		var stores = IntStream.rangeClosed(1, count)
			.mapToObj(i -> store(count + 1 - i, 1, i)).toList();
		// when
		var result = RecommendationSelector.select(
			List.of(new RankedCell(1L, 100)), stores, null, null);
		// then
		assertThat(result).hasSize(Math.min(30, count));
		assertThat(result.getFirst().id()).isEqualTo(1L);
		assertThat(result.getLast().id()).isEqualTo((long) Math.min(30, count));
	}

	@Test
	@DisplayName("후보가 아닌 인접 칸은 추천에 섞이지 않는다")
	void noNeighborExpansion() {
		// given / when
		var result = RecommendationSelector.select(List.of(new RankedCell(1L, 100)),
			List.of(store(1, 2, 10)), null, null);
		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("동점 칸은 평균 백분위를 공유하므로 같은 구간에서 거리순이다")
	void tiesShareBand() {
		// given: 동점 세 칸은 평균 백분위 75로 모두 60+ 구간이다.
		var cells = List.of(new RankedCell(1L, 1), new RankedCell(2L, 10),
			new RankedCell(3L, 10), new RankedCell(4L, 10));
		// when
		var result = RecommendationSelector.select(cells,
			List.of(store(1, 4, 300), store(2, 2, 100), store(3, 3, 200)), 0.0, 0.0);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("500m 경계 안쪽은 낮은 점수여도 경계 바깥보다 먼저 선택된다")
	void radiusBoundary() {
		// given / when
		var result = RecommendationSelector.select(
			List.of(new RankedCell(1L, 100), new RankedCell(2L, 1)),
			List.of(store(1, 1, 500.01), store(2, 2, 499.99)), 0.0, 0.0);
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 1L);
	}

	private StoreCardRow store(long id, long cell, double meters) {
		double lat = Math.toDegrees(meters / 6371000);
		return new StoreCardRow(id, "가게", StoreKind.KOREAN, cell, Area.INSADONG,
			lat, 0, null, "place-" + id);
	}
}
