package com.odyssey.health;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@link HealthController} 의 웹 계층 테스트.
 *
 * <p>{@link WebMvcTest} 는 컨트롤러와 그 주변(MessageConverter, ControllerAdvice)만 띄운다.
 * DataSource / JPA / Flyway 는 올라오지 않으므로 postgres 컨테이너가 필요 없다.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("서버가 떠 있으면 /health 는 공통 응답 형식으로 Status Ok 를 준다")
	void health() throws Exception {
		// given
		MockHttpServletRequestBuilder request = get("/health");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.Status").value("Ok"))
			// doesNotExist() 는 non-null 값이 없는지만 본다. error 키가 통째로 빠져도
			// 통과하므로, 키가 있는지와 값이 null 인지를 나눠서 검증한다.
			.andExpect(jsonPath("$.error").hasJsonPath())
			.andExpect(jsonPath("$.error").value(nullValue()));
	}
}
