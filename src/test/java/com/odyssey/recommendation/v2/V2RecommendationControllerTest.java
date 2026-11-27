package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.store.entity.Category;
import com.odyssey.store.StoreNames;
import com.odyssey.store.entity.StoreKind;

/** {@link V2RecommendationController} 의 웹 계층 테스트. 서비스는 가짜라서 요청 해석과 응답 모양만 본다. */
@WebMvcTest(V2RecommendationController.class)
class V2RecommendationControllerTest {

	private static final String URL = "/api/v2/recommendations";
	private static final V2Context CONTEXT =
		new V2Context("nov", "2025-11", Weekday.SUN, TimeSlot.DINNER);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2RecommendationService service;

	private static MockHttpServletRequestBuilder valid() {
		return get(URL).param("datasetId", "nov").param("weekday", "sun").param("slot", "dinner")
			.param("lat", "37.5655").param("lng", "126.9905");
	}

	/** 필수 값 다섯 개를 채운 요청에서 key 의 값만 value 로 바꾼다. */
	private static MockHttpServletRequestBuilder validWith(String key, String value) {
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("datasetId", "nov");
		params.set("weekday", "sun");
		params.set("slot", "dinner");
		params.set("lat", "37.5655");
		params.set("lng", "126.9905");
		params.set(key, value);
		return get(URL).params(params);
	}

	private static V2RecommendationResponse empty() {
		return new V2RecommendationResponse("NO_MATCHES", 0, CONTEXT, List.of());
	}

	@Test
	@DisplayName("필수 값만 보낸다 → 기본값 food · all · 빈 검색어로 서비스에 넘긴다")
	void defaultsArePassedToService() throws Exception {
		// given
		given(service.recommend(any())).willReturn(empty());

		// when
		mockMvc.perform(valid()).andExpect(status().isOk());

		// then
		ArgumentCaptor<V2RecommendationQuery> captor =
			ArgumentCaptor.forClass(V2RecommendationQuery.class);
		then(service).should().recommend(captor.capture());
		V2RecommendationQuery query = captor.getValue();
		assertThat(query.datasetId()).isEqualTo("nov");
		assertThat(query.weekday()).isEqualTo(Weekday.SUN);
		assertThat(query.slot()).isEqualTo(TimeSlot.DINNER);
		assertThat(query.origin().lat()).isEqualTo(37.5655);
		assertThat(query.origin().lng()).isEqualTo(126.9905);
		assertThat(query.category()).isEqualTo(Category.FOOD);
		assertThat(query.kind()).isNull();
		assertThat(query.q()).isEmpty();
	}

	@Test
	@DisplayName("선택 값을 명시한다 → 그 값으로 서비스에 넘기고 all 은 조건 없음(null)이다")
	void explicitValuesArePassedToService() throws Exception {
		// given
		given(service.recommend(any())).willReturn(empty());

		// when
		mockMvc.perform(valid().param("category", "all").param("kind", "korean")
			.param("q", " 스타 ")).andExpect(status().isOk());

		// then
		ArgumentCaptor<V2RecommendationQuery> captor =
			ArgumentCaptor.forClass(V2RecommendationQuery.class);
		then(service).should().recommend(captor.capture());
		V2RecommendationQuery query = captor.getValue();
		assertThat(query.category()).isNull();
		assertThat(query.kind()).isEqualTo(StoreKind.KOREAN);
		assertThat(query.q()).isEqualTo("스타");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "EN", "fr"})
	@DisplayName("잘못된 lang은 400으로 거부하고 서비스를 호출하지 않는다")
	void rejectsInvalidLanguage(String lang) throws Exception {
		mockMvc.perform(valid().param("lang", lang))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("lang이 두 번 오면 400으로 거부하고 서비스를 호출하지 않는다")
	void rejectsRepeatedLanguage() throws Exception {
		mockMvc.perform(valid().param("lang", "en", "ja"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("추천 목록의 상호를 요청한 언어로 표시한다")
	void localizesRecommendationName() throws Exception {
		given(service.recommend(any())).willReturn(new V2RecommendationResponse("OK", 1, CONTEXT,
			List.of(new Item(7, "공평식당", "101", StoreKind.KOREAN, 37.57, 126.98, 11.4, "place-7", Area.JONGNO,
				new StoreNames("Gongpyeong Restaurant", "公平食堂", "公平食堂")))));

		mockMvc.perform(valid().param("lang", "zh"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].name").value("公平食堂"));
	}

	@Test
	@DisplayName("목록이 있다 → 200 과 status, total, context, items 를 주고 enum 은 소문자다")
	void returnsItemsInEnvelope() throws Exception {
		// given
		given(service.recommend(any())).willReturn(new V2RecommendationResponse("OK", 1, CONTEXT,
			List.of(new Item(1676, "제주침시술소 을지로점", "81607938", StoreKind.KOREAN, 37.5655,
				126.9904598, 11.4, "place-1676", Area.EULJIRO))));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("OK"))
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.context.datasetId").value("nov"))
			.andExpect(jsonPath("$.data.context.referenceMonth").value("2025-11"))
			.andExpect(jsonPath("$.data.context.weekday").value("sun"))
			.andExpect(jsonPath("$.data.context.slot").value("dinner"))
			.andExpect(jsonPath("$.data.items[0].id").value(1676))
			.andExpect(jsonPath("$.data.items[0].cellId").value("81607938"))
			.andExpect(jsonPath("$.data.items[0].kind").value("korean"))
			.andExpect(jsonPath("$.data.items[0].distanceMeters").value(11.4))
			.andExpect(jsonPath("$.data.items[0].googlePlaceId").value("place-1676"))
			.andExpect(jsonPath("$.data.items[0].areaId").value("euljiro"))
			.andExpect(jsonPath("$.data.items[0].length()").value(9))
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	@DisplayName("조건에 맞는 매장이 없다 → 오류가 아니라 200 NO_MATCHES 와 빈 목록이다")
	void noMatchesIsSuccess() throws Exception {
		// given
		given(service.recommend(any())).willReturn(empty());

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("NO_MATCHES"))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	@DisplayName("필수 값 datasetId 가 없다 → 400 INVALID_INPUT 과 \"datasetId 값이 필요합니다.\"")
	void missingDatasetId() throws Exception {
		// when & then
		mockMvc.perform(get(URL).param("weekday", "sun").param("slot", "dinner")
			.param("lat", "37.5655").param("lng", "126.9905"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("datasetId 값이 필요합니다."));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"weekday,mon_thu", "weekday,SUN", "weekday,''", "slot,evening", "slot,''",
		"lat,NaN", "lat,91", "lng,Infinity", "lng,181", "lat,abc", "category,drink",
		"kind,noodle", "datasetId,a b"})
	@DisplayName("값이 잘못됐다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidValues(String key, String value) throws Exception {
		// when & then
		mockMvc.perform(validWith(key, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).should(never()).recommend(any());
	}

	@Test
	@DisplayName("서로 맞지 않는 category 와 kind → 400 INVALID_INPUT")
	void mismatchedCategoryAndKind() throws Exception {
		// when & then
		mockMvc.perform(valid().param("category", "food").param("kind", "cafe"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("category와 kind가 서로 맞지 않습니다."));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"radiusMeters,300", "limit,30", "density,lively", "ordering,visit_first"})
	@DisplayName("받지 않는 키를 보낸다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void unsupportedKeys(String key, String value) throws Exception {
		// when & then
		mockMvc.perform(valid().param(key, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않거나 중복된 요청 조건: " + key));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("같은 키를 두 번 보낸다 → 400 INVALID_INPUT")
	void duplicatedKey() throws Exception {
		// when & then
		mockMvc.perform(valid().param("kind", "bar", "cafe"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}
}
