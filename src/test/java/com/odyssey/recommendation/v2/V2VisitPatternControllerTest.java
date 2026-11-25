package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.Bar;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.Graph;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.PatternContext;

/** {@link V2VisitPatternController} 의 웹 계층 테스트. 서비스는 가짜라서 요청 해석과 응답 모양만 본다. */
@WebMvcTest(V2VisitPatternController.class)
class V2VisitPatternControllerTest {

	private static final String URL = "/api/v2/cells/{cellId}/visit-pattern";
	private static final PatternContext CONTEXT =
		new PatternContext("nov", "2025-11", "81591938", Weekday.SUN);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private V2VisitPatternService service;

	private static MockHttpServletRequestBuilder valid() {
		return requestWith("weekday", "sun");
	}

	private static MockHttpServletRequestBuilder requestWith(String key, String value) {
		LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("datasetId", "nov");
		params.set("weekday", "sun");
		params.set(key, value);
		return get(URL, 81591938L).params(params);
	}

	@Test
	@DisplayName("AVAILABLE 프로필 → 200 과 상태 · 그래프 · 소문자 시간대와 요일을 준다")
	void returnsGraph() throws Exception {
		// given
		Graph graph = new Graph("visit", "SAME_CELL_SAME_WEEKDAY", 100, TimeSlot.DINNER, List.of(
			new Bar(TimeSlot.LUNCH, 70), new Bar(TimeSlot.AFTERNOON, 75),
			new Bar(TimeSlot.DINNER, 100), new Bar(TimeSlot.LATE_NIGHT, 70)));
		given(service.visitPattern(any()))
			.willReturn(new V2VisitPatternResponse(CONTEXT, "AVAILABLE", graph));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.context.datasetId").value("nov"))
			.andExpect(jsonPath("$.data.context.referenceMonth").value("2025-11"))
			.andExpect(jsonPath("$.data.context.cellId").value("81591938"))
			.andExpect(jsonPath("$.data.context.weekday").value("sun"))
			.andExpect(jsonPath("$.data.status").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.graph.metric").value("visit"))
			.andExpect(jsonPath("$.data.graph.comparison").value("SAME_CELL_SAME_WEEKDAY"))
			.andExpect(jsonPath("$.data.graph.scaleMax").value(100))
			.andExpect(jsonPath("$.data.graph.highlightSlot").value("dinner"))
			.andExpect(jsonPath("$.data.graph.bars[0].slot").value("lunch"))
			.andExpect(jsonPath("$.data.graph.bars[0].relativeHeight").value(70))
			.andExpect(jsonPath("$.data.graph.bars[3].slot").value("late_night"))
			.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	@DisplayName("빠진 시간대가 있는 그래프 → 그 막대와 강조 시간대는 키를 남기고 값은 null 이다")
	void missingSlotKeepsNullHeight() throws Exception {
		// given
		Graph graph = new Graph("visit", "SAME_CELL_SAME_WEEKDAY", 100, null, List.of(
			new Bar(TimeSlot.LUNCH, null), new Bar(TimeSlot.AFTERNOON, 100),
			new Bar(TimeSlot.DINNER, 60), new Bar(TimeSlot.LATE_NIGHT, null)));
		given(service.visitPattern(any()))
			.willReturn(new V2VisitPatternResponse(CONTEXT, "INCOMPLETE_TIME_SLOTS", graph));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("INCOMPLETE_TIME_SLOTS"))
			.andExpect(jsonPath("$.data.graph.highlightSlot").hasJsonPath())
			.andExpect(jsonPath("$.data.graph.highlightSlot").isEmpty())
			.andExpect(jsonPath("$.data.graph.bars[0].relativeHeight").hasJsonPath())
			.andExpect(jsonPath("$.data.graph.bars[0].relativeHeight").isEmpty())
			.andExpect(jsonPath("$.data.graph.bars[1].relativeHeight").value(100));
	}

	@Test
	@DisplayName("막대가 없는 프로필 → 200 이고 graph 키는 남기고 값은 null 이다")
	void nullGraphKeepsKey() throws Exception {
		// given
		given(service.visitPattern(any()))
			.willReturn(new V2VisitPatternResponse(CONTEXT, "INCOMPLETE_TIME_SLOTS", null));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("INCOMPLETE_TIME_SLOTS"))
			.andExpect(jsonPath("$.data.graph").hasJsonPath())
			.andExpect(jsonPath("$.data.graph").isEmpty());
	}

	@Test
	@DisplayName("요청한다 → 경로의 칸 번호와 배치 · 요일을 서비스에 넘긴다")
	void bindsRequest() throws Exception {
		// given
		given(service.visitPattern(any()))
			.willReturn(new V2VisitPatternResponse(CONTEXT, "NO_DISTINCT_PEAK", null));

		// when
		mockMvc.perform(valid()).andExpect(status().isOk());

		// then
		ArgumentCaptor<V2VisitPatternQuery> captor =
			ArgumentCaptor.forClass(V2VisitPatternQuery.class);
		then(service).should().visitPattern(captor.capture());
		assertThat(captor.getValue())
			.isEqualTo(new V2VisitPatternQuery("nov", Weekday.SUN, 81591938L));
	}

	@Test
	@DisplayName("slot 을 보낸다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void slotIsRejected() throws Exception {
		// when & then
		mockMvc.perform(requestWith("slot", "dinner"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("지원하지 않거나 중복된 요청 조건: slot"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("필수 값 weekday 가 없다 → 400 INVALID_INPUT 과 \"weekday 값이 필요합니다.\"")
	void missingWeekday() throws Exception {
		// when & then
		mockMvc.perform(get(URL, 1L).param("datasetId", "nov"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value("weekday 값이 필요합니다."));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"weekday,mon_thu", "weekday,SUN", "datasetId,a b"})
	@DisplayName("요일이나 datasetId 가 잘못됐다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidValues(String key, String value) throws Exception {
		// when & then
		mockMvc.perform(requestWith(key, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@CsvSource({"0", "-5", "abc"})
	@DisplayName("칸 번호가 0 이하이거나 숫자가 아니다 → 400 INVALID_INPUT 이고 서비스는 부르지 않는다")
	void invalidCellId(String cellId) throws Exception {
		// when & then
		mockMvc.perform(get(URL, cellId).param("datasetId", "nov").param("weekday", "sun"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("서비스가 CELL_NOT_FOUND 를 던진다 → 404 와 error.code CELL_NOT_FOUND")
	void cellNotFound() throws Exception {
		// given
		given(service.visitPattern(any())).willThrow(
			new BusinessException(V2Error.CELL_NOT_FOUND, "해당 배치에 셀 자료가 없습니다."));

		// when & then
		mockMvc.perform(valid())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("CELL_NOT_FOUND"));
	}
}
