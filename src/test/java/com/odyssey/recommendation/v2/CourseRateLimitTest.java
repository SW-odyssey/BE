package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseSchedule.AfternoonPreference;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;

/**
 * 코스 API 의 요청 한도. 한도를 넘은 요청은 429 로 막혀 서비스(= Google 조회)까지 가지 않아야 한다.
 * 한도는 시험용으로 작게 줄여 둔다: 클라이언트당 2번, 서버 전체 3번.
 * 한도 상태는 앱 컨텍스트 안에 남으므로, 시험마다 컨텍스트를 새로 만들어 앞 시험의 요청이 섞이지 않게 한다.
 */
@WebMvcTest(V2CourseController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {
	"rate-limit.course.enabled=true",
	"rate-limit.course.client-burst=2",
	"rate-limit.course.client-per-minute=1",
	"rate-limit.course.global-burst=3",
	"rate-limit.course.global-per-minute=1",
	"cors.allowed-origins=http://localhost:3000"
})
class CourseRateLimitTest {

	private static final String URL = "/api/v2/course-recommendations";
	private static final V2CourseResponse EMPTY = new V2CourseResponse("PARTIAL",
		new V2Context("nov", "2025-11", Weekday.FRI, TimeSlot.DINNER), 0, java.util.List.of(), java.util.List.of(),
		new Interruption(TimeSlot.DINNER, "NO_VERIFIED_MATCH"));

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private V2CourseService service;

	@BeforeEach
	void anyCourse() {
		given(service.recommend(any(), any(), any(), any(), anySet(), anyLong(),
			any(AfternoonPreference.class), anyBoolean(), any(), anySet())).willReturn(EMPTY);
	}

	private static MockHttpServletRequestBuilder courseFrom(String address) {
		return get(URL)
			.param("datasetId", "nov")
			.param("weekday", "fri")
			.param("startSlot", "dinner")
			.param("lat", "37.5655342")
			.param("lng", "126.9903373")
			.with(request -> {
				request.setRemoteAddr(address);
				return request;
			});
	}

	private ResultActions call(String address) throws Exception {
		return mvc.perform(courseFrom(address));
	}

	@Test
	@DisplayName("한 클라이언트가 한도를 넘기면 429 와 Retry-After 로 거절하고 서비스는 부르지 않는다")
	void deniesClientOverLimit() throws Exception {
		// given: 한도 2번
		call("10.0.0.1").andExpect(status().isOk());
		call("10.0.0.1").andExpect(status().isOk());

		// when & then
		call("10.0.0.1")
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
		then(service).should(times(2)).recommend(any(), any(), any(), any(), anySet(), anyLong(),
			any(AfternoonPreference.class), anyBoolean(), any(), anySet());
	}

	@Test
	@DisplayName("Retry-After 는 1 이상의 초 단위 숫자다")
	void retryAfterIsPositiveSeconds() throws Exception {
		// given
		call("10.0.0.2");
		call("10.0.0.2");

		// when
		String retryAfter = call("10.0.0.2").andReturn().getResponse().getHeader("Retry-After");

		// then
		assertThat(retryAfter).isNotNull();
		assertThat(Long.parseLong(retryAfter)).isPositive();
	}

	@Test
	@DisplayName("다른 클라이언트는 한 클라이언트가 한도를 써도 영향받지 않는다")
	void otherClientIsNotAffected() throws Exception {
		// given
		call("10.0.1.1");
		call("10.0.1.1");
		call("10.0.1.1").andExpect(status().isTooManyRequests());

		// when & then
		call("10.0.1.2").andExpect(status().isOk());
	}

	@Test
	@DisplayName("서버 전체 한도를 넘으면 주소가 달라도 거절한다")
	void deniesWhenGlobalLimitReached() throws Exception {
		// given: 서버 전체 3번
		call("10.0.2.1").andExpect(status().isOk());
		call("10.0.2.2").andExpect(status().isOk());
		call("10.0.2.3").andExpect(status().isOk());

		// when & then
		call("10.0.2.4")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
	}

	@Test
	@DisplayName("한도를 다 쓴 뒤에도 CORS 사전 요청(OPTIONS)은 세지 않고 막지 않는다")
	void preflightIsNotCounted() throws Exception {
		// given: 이 클라이언트는 한도를 다 썼다
		call("10.0.3.1");
		call("10.0.3.1");
		call("10.0.3.1").andExpect(status().isTooManyRequests());

		// when & then
		mvc.perform(options(URL)
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET")
				.with(request -> {
					request.setRemoteAddr("10.0.3.1");
					return request;
				}))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("429 응답에도 CORS 헤더가 붙어 브라우저가 오류를 읽을 수 있다")
	void rejectionKeepsCorsHeaders() throws Exception {
		// given
		call("10.0.4.1");
		call("10.0.4.1");

		// when & then
		mvc.perform(courseFrom("10.0.4.1").header("Origin", "http://localhost:3000"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
	}
}
