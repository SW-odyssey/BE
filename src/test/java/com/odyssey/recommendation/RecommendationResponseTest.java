package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.entity.Cell;
import com.odyssey.cell.entity.CellFixture;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.odyssey.store.repository.StoreCardRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecommendationResponse} 가 가게 · 칸을 #30 응답 모양으로 바꾸는지 확인한다.
 *
 * <p>JSON 이름과 소문자 enum 은 {@link RecommendationControllerTest} 가 본다. 여기서는 값만 본다.
 */
class RecommendationResponseTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);

	@Test
	@DisplayName("칸 모양은 [경도, 위도] 점으로 남서에서 시작해 반시계로 돌고 남서에서 닫힌다")
	void cellShapeRing() {
		// given: 남서 (37.5698, 126.9797), 북동 (37.5702, 126.9803)
		Cell cell = CellFixture.cell(81_383_966L, 37.5698, 126.9797, 37.5702, 126.9803);

		// when
		RecommendationResponse.CellShape shape = RecommendationResponse.CellShape.from(cell);

		// then: 위도 · 경도가 뒤집히면 오류 없이 지도에 엉뚱한 곳이 칠해진다. 그래서 점 하나하나를 본다
		assertThat(shape.id()).isEqualTo(81_383_966L);
		assertThat(shape.geometry().type()).isEqualTo("Polygon");
		assertThat(shape.geometry().coordinates()[0]).isDeepEqualTo(new double[][] {
			{126.9797, 37.5698},   // 남서
			{126.9803, 37.5698},   // 남동
			{126.9803, 37.5702},   // 북동
			{126.9797, 37.5702},   // 북서
			{126.9797, 37.5698}}); // 남서 (닫기)
	}

	@Test
	@DisplayName("카드의 since 는 인허가일의 연도다")
	void sinceIsYear() {
		// given: 1968-03-11 인허가
		StoreCardRow row = store(1L, LocalDate.of(1968, 3, 11));

		// when
		RecommendationResponse.Item item = RecommendationResponse.Item.from(row);

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
			MONDAY, DayType.WEEKDAY, TimeSlot.AFTERNOON, stores, List.of());

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
			visitDate, DayType.WEEKDAY, TimeSlot.AFTERNOON);

		// then: 결과가 없어도 FE 는 "평일 오후 기준" 같은 문구를 띄운다
		assertThat(response.applied())
			.isEqualTo(new RecommendationResponse.Applied(MONDAY, DayType.WEEKDAY, TimeSlot.AFTERNOON));
		assertThat(response.total()).isZero();
		assertThat(response.items()).isEmpty();
		assertThat(response.cells()).isEmpty();
	}

	private static StoreCardRow store(long id, LocalDate licenseDate) {
		return new StoreCardRow(
			id,
			"가게" + id,
			Category.FOOD,
			FoodType.KOREAN,
			81_383_966L,
			Area.INSADONG,
			37.57,
			126.98,
			licenseDate,
			null);              // googlePlaceId
	}
}
