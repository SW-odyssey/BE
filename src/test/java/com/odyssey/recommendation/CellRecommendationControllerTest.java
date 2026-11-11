package com.odyssey.recommendation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CellRecommendationController.class)
class CellRecommendationControllerTest {

	private static final String URL = "/api/v1/recommendations/cell";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CellRecommendationService service;

	@Test
	@DisplayName("추천에서 받은 셀 ID 목록으로 경계를 조회한다")
	void cells() throws Exception {
		// given
		given(service.findCells(List.of(3L, 1L))).willReturn(new CellRecommendationResponse(List.of(
			new CellRecommendationResponse.CellItem(1L,
				new CellRecommendationResponse.Bounds(37.5698, 126.9797, 37.5702, 126.9803)))));

		// when
		var result = mockMvc.perform(get(URL).param("cellIds", "3,1"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.cells.length()").value(1))
			.andExpect(jsonPath("$.data.cells[0].cellId").value(1))
			.andExpect(jsonPath("$.data.cells[0].bounds.south").value(37.5698))
			.andExpect(jsonPath("$.data.cells[0].bounds.west").value(126.9797))
			.andExpect(jsonPath("$.data.cells[0].bounds.north").value(37.5702))
			.andExpect(jsonPath("$.data.cells[0].bounds.east").value(126.9803));
		then(service).should().findCells(List.of(3L, 1L));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"0", "-1", "abc", "1,,2", "9223372036854775808"})
	@DisplayName("ID가 없거나 양의 정수가 아니면 400으로 거절한다")
	void invalidIds(String ids) throws Exception {
		// given
		var request = get(URL);
		if (ids != null) {
			request.param("cellIds", ids);
		}

		// when
		var result = mockMvc.perform(request);

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").isNotEmpty());
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("추천 최대 개수인 30개 ID는 조회할 수 있다")
	void maximumIds() throws Exception {
		// given
		List<Long> ids = LongStream.rangeClosed(1, 30).boxed().toList();
		given(service.findCells(ids)).willReturn(new CellRecommendationResponse(List.of()));
		String query = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

		// when
		var result = mockMvc.perform(get(URL).param("cellIds", query));

		// then
		result.andExpect(status().isOk()).andExpect(jsonPath("$.data.cells").isEmpty());
		then(service).should().findCells(ids);
	}

	@Test
	@DisplayName("31개 ID를 보내면 조회하지 않고 400으로 거절한다")
	void tooManyIds() throws Exception {
		// given
		String query = LongStream.rangeClosed(1, 31).mapToObj(String::valueOf)
			.collect(Collectors.joining(","));

		// when
		var result = mockMvc.perform(get(URL).param("cellIds", query));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").isNotEmpty());
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("존재하는 셀이 없으면 성공과 빈 목록을 반환한다")
	void emptyCells() throws Exception {
		// given
		given(service.findCells(anyList())).willReturn(new CellRecommendationResponse(List.of()));

		// when
		var result = mockMvc.perform(get(URL).param("cellIds", "999"));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.cells").isEmpty());
	}
}
