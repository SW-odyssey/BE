package com.odyssey.recommendation.v2;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.StoreNames;
import com.odyssey.store.entity.StoreKind;

/** {@link V2PlaceController} 의 웹 계층 테스트. 서비스는 가짜라서 응답 모양과 요청 검증만 본다. */
@WebMvcTest(V2PlaceController.class)
class V2PlaceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2PlaceService service;

	@Test
	@DisplayName("있는 장소 → 200 과 정해진 여덟 필드만 준다")
	void returnsPlaceFields() throws Exception {
		// given
		given(service.place(1504L)).willReturn(new V2PlaceResponse(1504, "골목식당", "81591938",
			StoreKind.KOREAN, 37.5655, 126.9904598, LocalDate.of(2011, 6, 7), "place-1504"));

		// when & then
		mockMvc.perform(get("/api/v2/places/1504"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(8))
			.andExpect(jsonPath("$.data.id").value(1504))
			.andExpect(jsonPath("$.data.name").value("골목식당"))
			.andExpect(jsonPath("$.data.cellId").value("81591938"))
			.andExpect(jsonPath("$.data.kind").value("korean"))
			.andExpect(jsonPath("$.data.lat").value(37.5655))
			.andExpect(jsonPath("$.data.lng").value(126.9904598))
			.andExpect(jsonPath("$.data.sinceDate").value("2011-06-07"))
			.andExpect(jsonPath("$.data.googlePlaceId").value("place-1504"))
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"ko, 공평식당", "en, Gongpyeong Restaurant", "zh, 公平食堂", "ja, 公平食堂"})
	@DisplayName("lang에 해당하는 상호를 기존 name 필드에 담는다")
	void returnsRequestedName(String lang, String expected) throws Exception {
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.KOREAN, 37.57, 126.98, null, "place-7",
			new StoreNames("Gongpyeong Restaurant", "公平食堂", "公平食堂")));

		mockMvc.perform(get("/api/v2/places/7").param("lang", lang))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value(expected))
			.andExpect(jsonPath("$.data.length()").value(8));
	}

	@Test
	@DisplayName("번역이 없으면 원본 상호를 준다")
	void fallsBackToOriginalName() throws Exception {
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.KOREAN, 37.57, 126.98, null, "place-7"));

		mockMvc.perform(get("/api/v2/places/7").param("lang", "ja"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("공평식당"));
	}

	@Test
	@DisplayName("개업일과 구글 ID 가 없다 → 두 키는 남기고 값은 null 이다")
	void keepsNullKeys() throws Exception {
		// given
		given(service.place(7L)).willReturn(new V2PlaceResponse(7, "공평식당", "101",
			StoreKind.CAFE, 37.57, 126.98, null, null));

		// when & then
		mockMvc.perform(get("/api/v2/places/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(8))
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
