package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2ReasonsResponse.CellReason;
import com.odyssey.recommendation.v2.V2ReasonsResponse.Reason;

/** {@link V2CellReasonController} 의 웹 계층 테스트. 서비스는 가짜라서 요청 해석과 응답 모양만 본다. */
@WebMvcTest(V2CellReasonController.class)
class V2CellReasonControllerTest {

	private static final String URL = "/api/v2/cells/reasons";
	private static final V2Context CONTEXT =
		new V2Context("nov", "2025-11", Weekday.SUN, TimeSlot.DINNER);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2CellReasonService service;

	private static MockHttpServletRequestBuilder requestWith(String key, String value) {
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("datasetId", "nov");
		params.set("weekday", "sun");
		params.set("slot", "dinner");
		params.set("cellIds", "81591938");
		params.set(key, value);
		return get(URL).params(params);
	}

	private static MockHttpServletRequestBuilder valid() {
		return requestWith("cellIds", "81591938");
	}

	@Test
	@DisplayName("이유를 조회한다 → 200 과 칸 번호 문자열 키, 대문자 상태, 이유 코드를 준다")
	void returnsReasonsByCell() throws Exception {
		// given
		Map<Long, CellReason> reasons = new LinkedHashMap<>();
		reasons.put(81591938L, new CellReason(CellReasonStatus.AVAILABLE,
			List.of(new Reason("VISIT_RELATIVE_HIGH"), new Reason("TIME_PEAK"))));
		given(service.reasons(any())).willReturn(new V2ReasonsResponse(CONTEXT, reasons));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.context.datasetId").value("nov"))
			.andExpect(jsonPath("$.data.context.referenceMonth").value("2025-11"))
			.andExpect(jsonPath("$.data.context.weekday").value("sun"))
			.andExpect(jsonPath("$.data.context.slot").value("dinner"))
			.andExpect(jsonPath("$.data.reasonsByCellId['81591938'].status").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.reasonsByCellId['81591938'].reasons[0].code")
				.value("VISIT_RELATIVE_HIGH"))
			.andExpect(jsonPath("$.data.reasonsByCellId['81591938'].reasons[1].code")
				.value("TIME_PEAK"))
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	@DisplayName("추천 근거가 없는 칸 → 상태는 대문자 이름 그대로이고 이유는 빈 배열이다")
	void statusWithoutReasons() throws Exception {
		// given
		given(service.reasons(any())).willReturn(new V2ReasonsResponse(CONTEXT,
			Map.of(999L, new CellReason(CellReasonStatus.CELL_NOT_IN_DATASET, List.of()))));

		// when & then
		mockMvc.perform(requestWith("cellIds", "999"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reasonsByCellId['999'].status")
				.value("CELL_NOT_IN_DATASET"))
			.andExpect(jsonPath("$.data.reasonsByCellId['999'].reasons").isEmpty());
	}

	@Test
	@DisplayName("쉼표로 구분한 cellIds → 요청한 순서의 숫자 목록으로 서비스에 넘긴다")
	void bindsCommaSeparatedCellIds() throws Exception {
		// given
		given(service.reasons(any())).willReturn(new V2ReasonsResponse(CONTEXT, Map.of()));

		// when
		mockMvc.perform(requestWith("cellIds", "3,1,2")).andExpect(status().isOk());

		// then
		ArgumentCaptor<V2CellReasonsQuery> captor =
			ArgumentCaptor.forClass(V2CellReasonsQuery.class);
		then(service).should().reasons(captor.capture());
		V2CellReasonsQuery query = captor.getValue();
		assertThat(query.datasetId()).isEqualTo("nov");
		assertThat(query.weekday()).isEqualTo(Weekday.SUN);
		assertThat(query.slot()).isEqualTo(TimeSlot.DINNER);
		assertThat(query.cellIds()).containsExactly(3L, 1L, 2L);
	}

	@Test
	@DisplayName("칸을 30개 보낸다 → 200 이다")
	void thirtyCellsAccepted() throws Exception {
		// given
		given(service.reasons(any())).willReturn(new V2ReasonsResponse(CONTEXT, Map.of()));
		String ids = LongStream.rangeClosed(1, 30).mapToObj(String::valueOf)
			.collect(Collectors.joining(","));

		// when & then
		mockMvc.perform(requestWith("cellIds", ids)).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "1,1", "0", "-1", "1,abc", "1,,2"})
	@DisplayName("cellIds 가 빈 값 · 중복 · 0 · 음수 · 숫자가 아님 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidCellIds(String cellIds) throws Exception {
		// when & then
		mockMvc.perform(requestWith("cellIds", cellIds))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("칸이 31개다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void thirtyOneCells() throws Exception {
		// given
		String ids = LongStream.rangeClosed(1, 31).mapToObj(String::valueOf)
			.collect(Collectors.joining(","));

		// when & then
		mockMvc.perform(requestWith("cellIds", ids))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("cellIds는 중복 없는 양의 정수 1~30개입니다."));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("필수 값 cellIds 가 없다 → 400 INVALID_INPUT 과 \"cellIds 값이 필요합니다.\"")
	void missingCellIds() throws Exception {
		// when & then
		mockMvc.perform(get(URL).param("datasetId", "nov").param("weekday", "sun")
			.param("slot", "dinner"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("cellIds 값이 필요합니다."));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"weekday,mon_thu", "weekday,SUN", "slot,evening", "datasetId,a b"})
	@DisplayName("요일 · 시간대 · datasetId 가 잘못됐다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidValues(String key, String value) throws Exception {
		// when & then
		mockMvc.perform(requestWith(key, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("받지 않는 키를 보낸다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void unsupportedKey() throws Exception {
		// when & then
		mockMvc.perform(requestWith("limit", "30"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않거나 중복된 요청 조건: limit"));
		then(service).shouldHaveNoInteractions();
	}
}
