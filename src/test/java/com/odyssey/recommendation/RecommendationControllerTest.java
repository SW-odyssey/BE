package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.entity.StoreBadge;
import java.util.Map;
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
		assertThat(query.page()).isZero();
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
	@DisplayName("음식점에 since=true 를 보내면 → 음식점 종류와 방문일 기준 30년 전 날짜가 조건이 된다")
	void foodFilters() throws Exception {
		// given: 30년 이상 된 한식

		// when
		mockMvc.perform(get(URL)
				.param("category", "food")
				.param("foodType", "korean")
				.param("visitDate", "2026-10-28")
				.param("since", "true"))
			.andExpect(status().isOk());

		// then: 방문일 2026-10-28 기준 30년 전
		RecommendationQuery query = capturedQuery();
		assertThat(query.category()).isEqualTo(Category.FOOD);
		assertThat(query.foodType()).isEqualTo(FoodType.KOREAN);
		assertThat(query.openedBefore()).isEqualTo(LocalDate.of(1996, 10, 28));
	}

	@Test
	@DisplayName("카페에 음식점 종류 · since 가 같이 오면 → 무시한다")
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
	@DisplayName("방문일이 오늘보다 앞이어도 → 그대로 서비스에 전달한다")
	void visitDatePast() throws Exception {
		// given: 어제. 방문일은 범위를 검사하지 않는다

		// when
		mockMvc.perform(get(URL).param("visitDate", "2026-10-25"))
			.andExpect(status().isOk());

		// then
		assertThat(capturedQuery().visitDate()).isEqualTo(LocalDate.of(2026, 10, 25));
	}

	@Test
	@DisplayName("방문일이 1년 넘게 뒤여도 → 그대로 서비스에 전달한다")
	void visitDateFarFuture() throws Exception {
		// given: 오늘 2026-10-26 에서 한참 뒤

		// when
		mockMvc.perform(get(URL).param("visitDate", "2030-01-01"))
			.andExpect(status().isOk());

		// then
		assertThat(capturedQuery().visitDate()).isEqualTo(LocalDate.of(2030, 1, 1));
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
	@ValueSource(strings = {"-1", "abc", "1.5", "2147483648"})
	@DisplayName("페이지가 음수이거나 정수 범위를 벗어나면 400 INVALID_INPUT")
	void invalidPage(String page) throws Exception {
		// given
		// when
		ResultActions result = mockMvc.perform(get(URL).param("page", page));

		// then
		expectInvalidInput(result);
		result.andExpect(jsonPath("$.error.message").isNotEmpty());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, Integer.MAX_VALUE})
	@DisplayName("0 이상의 페이지 번호는 서비스에 그대로 전달된다")
	void pageParameter(int page) throws Exception {
		// given
		// when
		var result = mockMvc.perform(get(URL).param("page", Integer.toString(page)));

		// then
		result.andExpect(status().isOk());
		assertThat(capturedQuery().page()).isEqualTo(page);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "abc", "1", "0", "yes", "on", "TRUE", "20", "-1", "2000000000"})
	@DisplayName("since 가 true · false 가 아니면 → 400 INVALID_INPUT")
	void invalidSince(String since) throws Exception {
		// given: since 는 음식점일 때만 읽으므로 음식점을 같이 보낸다.
		// 1 · yes · on 은 Boolean 변환이면 true 로 통과하는 값이라 문자열로 엄격하게 받는지 본다

		// when
		ResultActions result = mockMvc.perform(get(URL)
			.param("category", "food")
			.param("since", since));

		// then
		expectInvalidInput(result);
		result.andExpect(jsonPath("$.error.message").value("since 는 true 또는 false 입니다"));
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
		// given: 서비스가 공식 배지를 가진 가게 한 곳을 준다
		StoreCardRow store = new StoreCardRow(1L, "백부장집", StoreKind.KOREAN, 81_383_966L,
			Area.INSADONG, 37.57, 126.98, LocalDate.of(1968, 3, 11), null);
		given(recommendationService.recommend(any())).willReturn(RecommendationResponse.of(
			TODAY, DayType.MON_THU, TimeSlot.AFTERNOON,
			RecommendationPage.from(List.of(store), 0),
			Map.of(1L, List.of(StoreBadge.ORAE_GAGE))));

		// when
		ResultActions result = mockMvc.perform(get(URL));

		// then: FE 는 이 이름으로 읽는다. 필드 이름을 바꾸면 여기서 깨진다
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.applied.visitDate").value("2026-10-26"))
			.andExpect(jsonPath("$.data.applied.dayType").value("mon_thu"))
			.andExpect(jsonPath("$.data.applied.slot").value("afternoon"))
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.items[0].areaId").value("insadong"))
			.andExpect(jsonPath("$.data.items[0].category").value("food"))
			.andExpect(jsonPath("$.data.items[0].foodType").value("korean"))
			.andExpect(jsonPath("$.data.items[0].since").value(1968))
			.andExpect(jsonPath("$.data.items[0].point.lat").value(37.57))
			.andExpect(jsonPath("$.data.cells").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].badges[0]").value("orae_gage"));
	}


	@ParameterizedTest
	@ValueSource(strings = {"NaN", "Infinity", "91", "-91"})
	@DisplayName("유효하지 않은 위도는 400이다")
	void invalidLatitude(String lat) throws Exception {
		// given / when
		var result = mockMvc.perform(get(URL).param("lat", lat).param("lng", "127"));
		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("위치 한쪽만 보내면 400이다")
	void incompleteLocation() throws Exception {
		// given / when
		var result = mockMvc.perform(get(URL).param("lng", "127"));
		// then
		expectInvalidInput(result);
	}

	@Test
	@DisplayName("좌표를 함께 보내면 서비스에 전달한다")
	void location() throws Exception {
		// given / when
		mockMvc.perform(get(URL).param("lat", "37.57").param("lng", "126.98"))
			.andExpect(status().isOk());
		// then
		var query = capturedQuery();
		assertThat(query.lat()).isEqualTo(37.57);
		assertThat(query.lng()).isEqualTo(126.98);
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
