package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.entity.CellFixture;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.config.ClockConfig;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.odyssey.store.repository.StoreCardRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RecommendationController} 의 웹 계층 테스트.
 *
 * <p>요청 문자열이 어떤 조건으로 바뀌어 서비스에 넘어가는지, 잘못된 값이면 400 을 내는지 본다.
 * 서비스는 가짜라서 추천 로직은 돌지 않는다. 넘어간 조건은 ArgumentCaptor 로 꺼낸다.
 *
 * <p>"오늘"은 고정 시계로 정한다. UTC 로는 2026-10-25 이지만 서울로는 2026-10-26(월) 인 시각이다.
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

	private static final String URL = "/api/v1/recommendations";
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			// 2026-10-25 20:00 UTC = 2026-10-26 05:00 서울
			return Clock.fixed(Instant.parse("2026-10-25T20:00:00Z"), ClockConfig.SEOUL);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	// 스프링 컨테이너의 서비스 빈을 가짜로 바꾼다. 정해 두지 않으면 null 을 돌려준다
	@MockitoBean
	private RecommendationService recommendationService;

	@Test
	@DisplayName("방문일을 생략하면 서버가 UTC 로 전날이어도 서울 기준 오늘이다")
	void visitDateDefault() throws Exception {
		// given: 고정 시계가 서울 기준 2026-10-26 을 가리킨다

		// when
		mockMvc.perform(get(URL)).andExpect(status().isOk());

		// then
		assertThat(capturedQuery().visitDate()).isEqualTo(TODAY);
	}

	@Test
	@DisplayName("조건을 생략하면 시간대는 오후, 나머지는 전체 · 상관없음(null)이다")
	void omittedFilters() throws Exception {
		// given: 파라미터 없이 요청

		// when
		mockMvc.perform(get(URL)).andExpect(status().isOk());

		// then
		RecommendationQuery query = capturedQuery();
		assertThat(query.timeSlot()).isEqualTo(TimeSlot.AFTERNOON);
		assertThat(query.area()).isNull();
		assertThat(query.category()).isNull();
		assertThat(query.foodType()).isNull();
		assertThat(query.openedBefore()).isNull();
		assertThat(query.densityLevel()).isNull();
		assertThat(query.query()).isNull();
	}

	@Test
	@DisplayName("all · any 는 생략과 같이 전체 · 상관없음(null)이다")
	void wildcards() throws Exception {
		// given: FE 는 기본 선택지의 id 를 그대로 보낼 수 있다 (meta API 의 all · any)

		// when
		mockMvc.perform(get(URL)
				.param("areaId", "all")
				.param("category", "all")
				.param("density", "any"))
			.andExpect(status().isOk());

		// then
		RecommendationQuery query = capturedQuery();
		assertThat(query.area()).isNull();
		assertThat(query.category()).isNull();
		assertThat(query.densityLevel()).isNull();
	}

	@Test
	@DisplayName("#30 의 파라미터 이름(slot · q 등)으로 보낸 값이 서비스까지 전달된다")
	void parameterNames() throws Exception {
		// given: 이름이 하나라도 다르면 값이 조용히 무시되고 기본값으로 돈다

		// when
		mockMvc.perform(get(URL)
				.param("areaId", "insadong")
				.param("visitDate", "2026-10-28")
				.param("slot", "dinner")
				.param("density", "less_busy")
				.param("q", "onion"))
			.andExpect(status().isOk());

		// then
		RecommendationQuery query = capturedQuery();
		assertThat(query.area()).isEqualTo(Area.INSADONG);
		assertThat(query.visitDate()).isEqualTo(LocalDate.of(2026, 10, 28));
		assertThat(query.timeSlot()).isEqualTo(TimeSlot.DINNER);
		assertThat(query.densityLevel()).isEqualTo(DensityLevel.LESS_BUSY);
		assertThat(query.query()).isEqualTo("onion");
	}

	@Test
	@DisplayName("음식점이면 음식점 종류와 노포(since=true)의 30년 전 날짜가 조건이 된다")
	void foodFilters() throws Exception {
		// given: 30년 이상 된 한식

		// when
		mockMvc.perform(get(URL)
				.param("category", "food")
				.param("foodType", "korean")
				.param("since", "true"))
			.andExpect(status().isOk());

		// then: 오늘 2026-10-26 기준 30년 전
		RecommendationQuery query = capturedQuery();
		assertThat(query.category()).isEqualTo(Category.FOOD);
		assertThat(query.foodType()).isEqualTo(FoodType.KOREAN);
		assertThat(query.openedBefore()).isEqualTo(LocalDate.of(1996, 10, 26));
	}

	@Test
	@DisplayName("카페면 음식점 종류 · since 가 같이 와도 무시한다")
	void cafeIgnoresFoodFilters() throws Exception {
		// given: 화면을 오가며 음식점 조건이 남은 채로 카페를 고른 경우

		// when
		mockMvc.perform(get(URL)
				.param("category", "cafe")
				.param("foodType", "korean")
				.param("since", "true"))
			.andExpect(status().isOk());

		// then
		RecommendationQuery query = capturedQuery();
		assertThat(query.category()).isEqualTo(Category.CAFE);
		assertThat(query.foodType()).isNull();
		assertThat(query.openedBefore()).isNull();
	}

	@Test
	@DisplayName("since=false 면 노포 조건 없이(null) 음식점 전체를 찾는다")
	void sinceFalse() throws Exception {
		// given: 노포만 보기를 끈 음식점 검색

		// when
		mockMvc.perform(get(URL)
				.param("category", "food")
				.param("since", "false"))
			.andExpect(status().isOk());

		// then
		assertThat(capturedQuery().openedBefore()).isNull();
	}

	@Test
	@DisplayName("방문일은 오늘부터 1년 뒤까지 고를 수 있다")
	void visitDateLastDay() throws Exception {
		// given: 2026-10-26 + 1년

		// when
		mockMvc.perform(get(URL).param("visitDate", "2027-10-26"))
			.andExpect(status().isOk());

		// then
		assertThat(capturedQuery().visitDate()).isEqualTo(LocalDate.of(2027, 10, 26));
	}

	@Test
	@DisplayName("방문일이 오늘보다 앞이면 400 INVALID_INPUT")
	void visitDatePast() throws Exception {
		// given: 어제

		// when
		ResultActions result = mockMvc.perform(get(URL).param("visitDate", "2026-10-25"));

		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("방문일이 1년하고 하루 뒤면 400 INVALID_INPUT")
	void visitDateTooFar() throws Exception {
		// given: 2026-10-26 + 1년 + 1일

		// when
		ResultActions result = mockMvc.perform(get(URL).param("visitDate", "2027-10-27"));

		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("검색어는 80자까지 받는다")
	void queryMaxLength() throws Exception {
		// given
		String q = "가".repeat(80);

		// when
		mockMvc.perform(get(URL).param("q", q)).andExpect(status().isOk());

		// then
		assertThat(capturedQuery().query()).hasSize(80);
	}

	@Test
	@DisplayName("검색어가 81자면 400 INVALID_INPUT")
	void queryTooLong() throws Exception {
		// given
		String q = "가".repeat(81);

		// when
		ResultActions result = mockMvc.perform(get(URL).param("q", q));

		// then
		expectInvalidInput(result);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "20", "-1"})
	@DisplayName("since 가 true · false 가 아니면 400 INVALID_INPUT")
	void invalidSince(String since) throws Exception {
		// given: 예전 값(20 · 30 · 40)을 보내는 FE 가 있어도 조용히 무시하지 않고 알려 준다

		// when
		ResultActions result = mockMvc.perform(get(URL)
			.param("category", "food")
			.param("since", since));

		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("모르는 지역이면 400 INVALID_INPUT")
	void unknownArea() throws Exception {
		// given: 서비스 지역이 아닌 곳

		// when
		ResultActions result = mockMvc.perform(get(URL).param("areaId", "gangnam"));

		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("응답은 #30 의 필드 이름을 쓰고 enum 은 소문자로 나간다")
	void responseShape() throws Exception {
		// given: 서비스가 가게 한 곳과 칸 한 개를 준다
		StoreCardRow store = new StoreCardRow(1L, "백부장집", Category.FOOD, FoodType.KOREAN, 81_383_966L,
			Area.INSADONG, 37.57, 126.98, LocalDate.of(1968, 3, 11), null);
		given(recommendationService.recommend(any())).willReturn(RecommendationResponse.of(
			TODAY, DayType.WEEKDAY, TimeSlot.AFTERNOON,
			List.of(store),
			List.of(CellFixture.cell(81_383_966L, 37.5698, 126.9797, 37.5702, 126.9803))));

		// when
		ResultActions result = mockMvc.perform(get(URL));

		// then: FE 는 이 이름으로 읽는다. 필드 이름을 바꾸면 여기서 깨진다
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.applied.visitDate").value("2026-10-26"))
			.andExpect(jsonPath("$.data.applied.dayType").value("weekday"))
			.andExpect(jsonPath("$.data.applied.slot").value("afternoon"))
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[0].areaId").value("insadong"))
			.andExpect(jsonPath("$.data.items[0].category").value("food"))
			.andExpect(jsonPath("$.data.items[0].foodType").value("korean"))
			.andExpect(jsonPath("$.data.items[0].since").value(1968))
			.andExpect(jsonPath("$.data.items[0].point.lat").value(37.57))
			.andExpect(jsonPath("$.data.cells[0].id").value(81_383_966L))
			.andExpect(jsonPath("$.data.cells[0].geometry.type").value("Polygon"))
			.andExpect(jsonPath("$.data.cells[0].geometry.coordinates[0][0][0]").value(126.9797));
	}

	// 컨트롤러가 서비스에 넘긴 조건을 꺼낸다
	private RecommendationQuery capturedQuery() {
		ArgumentCaptor<RecommendationQuery> captor = ArgumentCaptor.forClass(RecommendationQuery.class);
		then(recommendationService).should().recommend(captor.capture());
		return captor.getValue();
	}

	// 400 INVALID_INPUT 이고, 잘못된 요청이라 서비스까지 가지 않았다
	private void expectInvalidInput(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(recommendationService).should(never()).recommend(any());
	}
}
