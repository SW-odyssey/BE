package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
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
		var result = RecommendationSelector.select(cells, stores, 0.0, 0.0, 0).stores();
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
		var result = RecommendationSelector.select(cells, stores, 0.0, 0.0, 0).stores();
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
			List.of(store(1, 1, 900), store(2, 2, 600)), 0.0, 0.0, 0).stores();
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
			List.of(store(1, 2, 10), store(3, 1, 10), store(2, 1, 20)), null, null, 0).stores();
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
			List.of(new RankedCell(1L, 100)), stores, null, null, 0).stores();
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
			List.of(store(1, 2, 10)), null, null, 0).stores();
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
			List.of(store(1, 4, 300), store(2, 2, 100), store(3, 3, 200)), 0.0, 0.0, 0).stores();
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("500m 경계 안쪽은 낮은 점수여도 경계 바깥보다 먼저 선택된다")
	void radiusBoundary() {
		// given / when
		var result = RecommendationSelector.select(
			List.of(new RankedCell(1L, 100), new RankedCell(2L, 1)),
			List.of(store(1, 1, 500.01), store(2, 2, 499.99)), 0.0, 0.0, 0).stores();
		// then
		assertThat(result).extracting(StoreCardRow::id).containsExactly(2L, 1L);
	}

	@ParameterizedTest
	@CsvSource({"0,0,0,false", "29,0,29,false", "30,0,30,false", "31,0,30,true",
		"30,1,0,false", "31,1,1,false", "60,0,30,true", "60,1,30,false",
		"61,1,30,true", "61,2,1,false", "61,3,0,false", "61,2147483647,0,false"})
	@DisplayName("요청한 페이지의 장소만 반환하고 다음 페이지가 있을 때만 hasNext가 참이다")
	void pageBoundaries(int count, int page, int expectedCount, boolean hasNext) {
		// given
		var stores = IntStream.rangeClosed(1, count)
			.mapToObj(i -> store(count + 1 - i, 1, 100)).toList();
		long firstId = (long) page * 30 + 1;
		var expectedIds = LongStream.range(firstId, firstId + expectedCount).boxed().toList();

		// when
		var result = RecommendationSelector.select(
			List.of(new RankedCell(1L, 100)), stores, null, null, page);

		// then
		assertThat(result.stores()).extracting(StoreCardRow::id).containsExactlyElementsOf(expectedIds);
		assertThat(result.hasNext()).isEqualTo(hasNext);
	}

	@Test
	@DisplayName("위치 추천도 전체 후보의 점수 구간과 거리순을 유지하며 페이지 사이 중복이 없다")
	void locationPagesKeepGlobalOrder() {
		// given: 높은 두 칸이 같은 구간이며, 가까운 61번 다음에 31~60번이 온다.
		var cells = IntStream.rangeClosed(1, 10)
			.mapToObj(i -> new RankedCell((long) i, i)).toList();
		var stores = new ArrayList<StoreCardRow>();
		IntStream.rangeClosed(1, 30).forEach(i -> stores.add(store(i, 1, 10)));
		IntStream.rangeClosed(31, 60).forEach(i -> stores.add(store(i, 10, 300)));
		stores.add(store(61, 9, 100));

		// when
		var first = RecommendationSelector.select(cells, stores, 0.0, 0.0, 0);
		var second = RecommendationSelector.select(cells, stores, 0.0, 0.0, 1);
		var last = RecommendationSelector.select(cells, stores, 0.0, 0.0, 2);
		var actual = Stream.of(first, second, last)
			.flatMap(page -> page.stores().stream()).map(StoreCardRow::id).toList();
		var expected = new ArrayList<Long>();
		expected.add(61L);
		expected.addAll(LongStream.rangeClosed(31, 60).boxed().toList());
		expected.addAll(LongStream.rangeClosed(1, 30).boxed().toList());

		// then
		assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
		assertThat(first.stores()).hasSize(30);
		assertThat(second.stores()).hasSize(30);
		assertThat(last.stores()).hasSize(1);
		assertThat(first.hasNext()).isTrue();
		assertThat(second.hasNext()).isTrue();
		assertThat(last.hasNext()).isFalse();
	}

	private StoreCardRow store(long id, long cell, double meters) {
		double lat = Math.toDegrees(meters / 6371000);
		return new StoreCardRow(id, "가게", StoreKind.KOREAN, cell, Area.INSADONG,
			lat, 0, null, "place-" + id);
	}
}
