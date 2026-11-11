package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StoreCardRow;

class RecommendationPageTest {
	@ParameterizedTest
	@CsvSource({"0,0,0,false", "29,0,29,false", "30,0,30,false", "31,0,30,true",
		"30,1,0,false", "31,1,1,false", "60,0,30,true", "60,1,30,false",
		"61,1,30,true", "61,2,1,false", "61,3,0,false", "61,2147483647,0,false"})
	@DisplayName("요청한 페이지의 장소만 반환하고 다음 페이지가 있을 때만 hasNext가 참이다")
	void pageBoundaries(int count, int page, int expectedCount, boolean hasNext) {
		// given
		var stores = IntStream.rangeClosed(1, count)
			.mapToObj(i -> store(i, 1, 100)).toList();
		long firstId = (long) page * 30 + 1;
		var expectedIds = LongStream.range(firstId, firstId + expectedCount).boxed().toList();

		// when
		var result = RecommendationPage.from(stores, page);

		// then
		assertThat(result.stores()).extracting(StoreCardRow::id).containsExactlyElementsOf(expectedIds);
		assertThat(result.number()).isEqualTo(page);
		assertThat(result.hasNext()).isEqualTo(hasNext);
	}

	@Test
	@DisplayName("원본 후보 목록이 바뀌어도 이미 만든 페이지는 유지된다")
	void preservesPageContents() {
		// given
		var stores = new ArrayList<>(List.of(store(1, 1, 100)));
		var page = RecommendationPage.from(stores, 0);

		// when
		stores.clear();

		// then
		assertThat(page.stores()).extracting(StoreCardRow::id).containsExactly(1L);
	}

	@Test
	@DisplayName("페이지 번호가 음수이면 페이지를 만들지 않는다")
	void rejectsNegativePage() {
		// given
		List<StoreCardRow> stores = List.of();

		// when
		var error = catchThrowableOfType(IllegalArgumentException.class,
			() -> RecommendationPage.from(stores, -1));

		// then
		assertThat(error).hasMessage("페이지 번호는 0 이상이어야 합니다");
	}

	private StoreCardRow store(long id, long cell, double meters) {
		double lat = Math.toDegrees(meters / 6371000);
		return new StoreCardRow(id, "가게", StoreKind.KOREAN, cell, Area.INSADONG,
			lat, 0, null, "place-" + id);
	}
}
