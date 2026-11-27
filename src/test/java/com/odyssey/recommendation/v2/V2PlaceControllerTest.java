package com.odyssey.recommendation.v2;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.odyssey.cell.entity.Area;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.StoreNames;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.StoreKind;

/** {@link V2PlaceController} 의 웹 계층 테스트. 서비스는 가짜라서 응답 모양과 요청 검증만 본다. */
@WebMvcTest(V2PlaceController.class)
class V2PlaceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2PlaceService service;

	@Test
	@DisplayName("전각·앞뒤 공백 검색어와 limit 생략 → 정규화한 검색어와 기본 6건으로 찾고 요청 언어 상호를 준다")
	void searchesWithNormalizedTermAndDefaultLimit() throws Exception {
		// given
		given(service.searchByName("Eulji", Language.JA, 6)).willReturn(List.of(new V2PlaceResponse(
			7, "ウルチカルメギ", "101", StoreKind.BAR, 37.57, 126.98, null, null, Area.EULJIRO)));

		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", " Ｅｕｌｊｉ ").param("lang", "ja"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].id").value(7))
			.andExpect(jsonPath("$.data.items[0].name").value("ウルチカルメギ"))
			.andExpect(jsonPath("$.data.items[0].kind").value("bar"));
	}

	@Test
	@DisplayName("일치하는 매장이 없다 → 200 과 빈 items")
	void returnsEmptyItemsWhenNothingMatches() throws Exception {
		// given
		given(service.searchByName("없는상호", Language.KO, 30)).willReturn(List.of());

		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", "없는상호").param("limit", "30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "　"})
	@DisplayName("빈 검색어 → 400 INVALID_INPUT 이고 서비스를 호출하지 않는다")
	void rejectsBlankSearchTerm(String searchTerm) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", searchTerm))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("이모지 80자 검색어 → 글자 수로 세어 받는다")
	void acceptsSearchTermOfEightyCharacters() throws Exception {
		// given
		String searchTerm = "😀".repeat(80);
		given(service.searchByName(searchTerm, Language.KO, 6)).willReturn(List.of());

		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", searchTerm))
			.andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"가", "😀"})
	@DisplayName("81자 검색어 → 400 INVALID_INPUT")
	void rejectsSearchTermLongerThanEightyCharacters(String character) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", character.repeat(81)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("q는 유효한 상호 검색어 1~80자로 입력해 주세요."));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "0", "31", "all"})
	@DisplayName("limit 이 비었거나 1~30 밖이거나 숫자가 아니다 → 400 INVALID_INPUT")
	void rejectsInvalidResultLimit(String limit) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", "식당").param("limit", limit))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("추천 조건 datasetId 를 보낸다 → 400 INVALID_INPUT")
	void rejectsRecommendationParameters() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/place-search").param("q", "식당").param("datasetId", "some-batch"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("있는 장소 → 200 과 정해진 아홉 필드만 준다")
	void returnsPlaceFields() throws Exception {
		// given
		given(service.place(1504L)).willReturn(new V2PlaceResponse(1504, "골목식당", "81591938",
			StoreKind.KOREAN, 37.5655, 126.9904598, LocalDate.of(2011, 6, 7), "place-1504", Area.JONGNO));

		// when & then
		mockMvc.perform(get("/api/v2/places/1504"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(9))
			.andExpect(jsonPath("$.data.id").value(1504))
			.andExpect(jsonPath("$.data.name").value("골목식당"))
			.andExpect(jsonPath("$.data.cellId").value("81591938"))
			.andExpect(jsonPath("$.data.kind").value("korean"))
			.andExpect(jsonPath("$.data.lat").value(37.5655))
			.andExpect(jsonPath("$.data.lng").value(126.9904598))
			.andExpect(jsonPath("$.data.sinceDate").value("2011-06-07"))
			.andExpect(jsonPath("$.data.googlePlaceId").value("place-1504"))
			.andExpect(jsonPath("$.data.areaId").value("jongno"))
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "EN", "fr"})
	@DisplayName("잘못된 lang은 400으로 거부하고 서비스를 호출하지 않는다")
	void rejectsInvalidLanguage(String lang) throws Exception {
		mockMvc.perform(get("/api/v2/places/7").param("lang", lang))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("lang이 두 번 오면 400으로 거부하고 서비스를 호출하지 않는다")
	void rejectsRepeatedLanguage() throws Exception {
		mockMvc.perform(get("/api/v2/places/7").param("lang", "en", "ja"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"ko, 공평식당", "en, Gongpyeong Restaurant", "zh, 公平食堂", "ja, 公平食堂"})
	@DisplayName("lang에 해당하는 상호를 기존 name 필드에 담는다")
	void returnsRequestedName(String lang, String expected) throws Exception {
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.KOREAN, 37.57, 126.98, null, "place-7", Area.JONGNO,
			new StoreNames("Gongpyeong Restaurant", "公平食堂", "公平食堂")));

		mockMvc.perform(get("/api/v2/places/7").param("lang", lang))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value(expected))
			.andExpect(jsonPath("$.data.length()").value(9));
	}

	@Test
	@DisplayName("번역이 없으면 원본 상호를 준다")
	void fallsBackToOriginalName() throws Exception {
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.KOREAN, 37.57, 126.98, null, "place-7", Area.JONGNO));

		mockMvc.perform(get("/api/v2/places/7").param("lang", "ja"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("공평식당"));
	}

	@Test
	@DisplayName("개업일과 구글 ID 가 없다 → 두 키는 남기고 값은 null 이다")
	void keepsNullKeys() throws Exception {
		// given
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.CAFE, 37.57, 126.98, null, null, Area.JONGNO));

		// when & then
		mockMvc.perform(get("/api/v2/places/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(9))
			.andExpect(jsonPath("$.data.sinceDate").hasJsonPath())
			.andExpect(jsonPath("$.data.sinceDate").isEmpty())
			.andExpect(jsonPath("$.data.googlePlaceId").hasJsonPath())
			.andExpect(jsonPath("$.data.googlePlaceId").isEmpty());
	}

	@Test
	@DisplayName("없는 장소 → 404 PLACE_NOT_FOUND")
	void notFound() throws Exception {
		// given
		given(service.place(999999L)).willThrow(new BusinessException(
			CommonErrorCode.PLACE_NOT_FOUND, "현재 제공하는 장소가 아닙니다."));

		// when & then
		mockMvc.perform(get("/api/v2/places/999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("현재 제공하는 장소가 아닙니다."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc"})
	@DisplayName("장소 번호가 양의 정수가 아니다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidId(String id) throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/places/" + id))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).should(never()).place(anyLong());
	}

	@Test
	@DisplayName("받지 않는 쿼리 파라미터를 보낸다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void unsupportedParam() throws Exception {
		// when & then
		mockMvc.perform(get("/api/v2/places/7").param("weekday", "mon"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않거나 중복된 요청 조건: weekday"));
		then(service).shouldHaveNoInteractions();
	}
}
