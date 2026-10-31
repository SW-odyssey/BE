package com.odyssey.metadata;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.odyssey.global.config.ClockConfig;

/**
 * {@link MetaController} 의 웹 계층 테스트. GitHub 이슈 #29 의 응답 모양을 확인한다.
 *
 * <p>"오늘"은 고정 시계로 정한다. UTC 로는 2026-10-23 이지만 서울로는 2026-10-24 가 되는 시각이라,
 * 서울 기준으로 계산하는지도 함께 확인된다.
 */
@WebMvcTest(MetaController.class)
class MetaControllerTest {

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			// 2026-10-23 20:00 UTC = 2026-10-24 05:00 서울
			return Clock.fixed(Instant.parse("2026-10-23T20:00:00Z"), ClockConfig.SEOUL);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("메타를 조회하면 공통 형식으로 성공 응답한다")
	void commonFormat() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.error").hasJsonPath())
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	@DisplayName("메타를 조회하면 서울 시간대와 지원 언어, 최대 결과 수를 알려 준다")
	void basics() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		result.andExpect(jsonPath("$.data.timeZone").value("Asia/Seoul"))
			.andExpect(jsonPath("$.data.supportedLanguages", contains("ko", "ja", "zh", "en")))
			.andExpect(jsonPath("$.data.maxResults").value(200));
	}

	@Test
	@DisplayName("서버 시각이 UTC 로는 전날이어도 방문 날짜는 서울 기준 오늘부터 6일 뒤까지다")
	void visitDate() throws Exception {
		// given: 고정 시계가 서울 기준 2026-10-24 를 가리킨다

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		result.andExpect(jsonPath("$.data.visitDate.min").value("2026-10-24"))
			.andExpect(jsonPath("$.data.visitDate.max").value("2026-10-30"));
	}

	@Test
	@DisplayName("지역은 전체가 기본이고 종로 · 익선동 · 인사동 · 을지로 · 명동이 이어진다")
	void areas() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "areas", "all",
			"all", "jongno", "ikseon", "insadong", "euljiro", "myeongdong");
	}

	@Test
	@DisplayName("카테고리는 전체가 기본이고 음식점 · 카페가 이어진다")
	void categories() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "categories", "all", "all", "food", "cafe");
	}

	@Test
	@DisplayName("음식점 종류는 상관없음이 기본이고 한식만 고를 수 있다")
	void foodTypes() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "foodTypes", "any", "any", "korean");
	}

	@Test
	@DisplayName("노포 기준은 상관없음이 기본이고 20 · 30 · 40년 이상이 이어진다")
	void since() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "since", "any", "any", "20", "30", "40");
	}

	@Test
	@DisplayName("시간대는 오후가 기본이고 점심 · 저녁 · 심야와 화면 표시명을 함께 준다")
	void slotsTime() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "slotsTime", "afternoon", "lunch", "afternoon", "dinner", "late_night");
		result.andExpect(jsonPath("$.data.slotsTime[0].label").value("점심 11~13시"))
			.andExpect(jsonPath("$.data.slotsTime[3].label").value("심야 22~01시"));
	}

	@Test
	@DisplayName("붐빔 정도는 상관없음이 기본이고 덜 붐빔 · 활기참만 고를 수 있다")
	void densities() throws Exception {
		// given

		// when
		ResultActions result = mockMvc.perform(get("/api/v1/meta"));

		// then
		expectOptions(result, "densities", "any", "any", "less_busy", "lively");
	}

	// 선택지의 id 순서와, isDefault 가 true 인 것이 defaultId 하나뿐인지 확인한다
	private void expectOptions(ResultActions result, String field, String defaultId, String... ids)
		throws Exception {
		result.andExpect(jsonPath("$.data." + field + "[*].id", contains(ids)))
			.andExpect(jsonPath("$.data." + field + "[?(@.isDefault == true)].id", contains(defaultId)));
	}
}
