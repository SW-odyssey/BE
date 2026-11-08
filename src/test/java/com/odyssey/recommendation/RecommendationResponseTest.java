package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.odyssey.store.repository.StoreCardRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.odyssey.store.entity.StoreKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecommendationResponse} 가 가게를 v3 카드 응답 모양으로 바꾸는지 확인한다.
 *
 * <p>JSON 이름과 소문자 enum 은 {@link RecommendationControllerTest} 가 본다. 여기서는 값만 본다.
 */
class RecommendationResponseTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

	@Test
	@DisplayName("카드의 since 는 이력 기준 개업일의 연도다")
	void sinceIsYear() {
		// given: 1968-03-11 인허가
		StoreCardRow row = store(1L, LocalDate.of(1968, 3, 11));

		// when
		RecommendationResponse.Item item = RecommendationResponse.Item.from(row, List.of());

		// then: 화면은 "1968년부터" 처럼 연도만 쓴다 (#30)
		assertThat(item.since()).isEqualTo(1968);
	}

	@Test
	@DisplayName("가게를 받은 순서 그대로 담고 total 은 가게 수다")
	void keepsOrder() {
		// given: 서비스가 이미 추천 순서로 정렬한 가게
		List<StoreCardRow> stores = List.of(
			store(3L, LocalDate.of(1990, 1, 1)),
			store(1L, LocalDate.of(1990, 1, 1)));

		// when
		RecommendationResponse response = RecommendationResponse.of(
			MONDAY, DayType.MON_THU, TimeSlot.AFTERNOON, stores, Map.of());

		// then: 응답이 다시 정렬하면 추천 순서가 깨진다
		assertThat(response.items())
			.extracting(RecommendationResponse.Item::id)
			.containsExactly(3L, 1L);
		assertThat(response.total()).isEqualTo(2);
	}

	@Test
	@DisplayName("빈 결과도 서버가 정한 방문일 · 요일 묶음 · 시간대는 채워 준다")
	void emptyKeepsApplied() {
		// given
		LocalDate visitDate = MONDAY;

		// when
		RecommendationResponse response = RecommendationResponse.empty(
			visitDate, DayType.MON_THU, TimeSlot.AFTERNOON);

		// then: 결과가 없어도 FE 는 "평일 오후 기준" 같은 문구를 띄운다
		assertThat(response.applied())
			.isEqualTo(new RecommendationResponse.Applied(MONDAY, DayType.MON_THU, TimeSlot.AFTERNOON));
		assertThat(response.total()).isZero();
		assertThat(response.items()).isEmpty();
	}

	private static StoreCardRow store(long id, LocalDate licenseDate) {
		return new StoreCardRow(
			id,
			"가게" + id,
			StoreKind.KOREAN,
			81_383_966L,
			Area.INSADONG,
			37.57,
			126.98,
			licenseDate,
			null);              // googlePlaceId
	}
}
