package com.odyssey.recommendation.v2;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;

/** {@link V2MetaController} 의 웹 계층 테스트. 서비스는 가짜라서 응답 모양과 요청 검증만 본다. */
@WebMvcTest(V2MetaController.class)
class V2MetaControllerTest {

	private static final String URL = "/api/v2/meta";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2MetaService service;

	private static V2MetaResponse meta(List<DatasetOption> datasets) {
		return new V2MetaResponse(datasets,
			List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
			List.of("lunch", "afternoon", "dinner", "late_night"),
			List.of("food", "cafe", "all"),
			List.of("korean", "bar", "other", "cafe", "all"));
	}

	@Test
	@DisplayName("선택지를 요청한다 → 200 과 success, data, error 틀로 준다")
	void returnsMetaInEnvelope() throws Exception {
		// given
		given(service.meta()).willReturn(
			meta(List.of(new DatasetOption("2025-12-b7786edeeae8", "2025-12", "DOMESTIC_20_39"))));

		// when & then
		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.datasets[0].id").value("2025-12-b7786edeeae8"))
			.andExpect(jsonPath("$.data.datasets[0].referenceMonth").value("2025-12"))
			.andExpect(jsonPath("$.data.datasets[0].population").value("DOMESTIC_20_39"))
			.andExpect(jsonPath("$.data.kinds[0]").value("korean"))
			.andExpect(jsonPath("$.error").hasJsonPath())
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	@DisplayName("적재 전이라 배치가 없다 → 200 이고 datasets 는 빈 배열이다")
	void emptyDatasets() throws Exception {
		// given
		given(service.meta()).willReturn(meta(List.of()));

		// when & then
		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.datasets").isArray())
			.andExpect(jsonPath("$.data.datasets").isEmpty());
	}

	@Test
	@DisplayName("받지 않는 쿼리 파라미터를 보낸다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void unsupportedParam() throws Exception {
		// when & then
		mockMvc.perform(get(URL).param("weekday", "mon"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않거나 중복된 요청 조건: weekday"));
		then(service).shouldHaveNoInteractions();
	}
}
