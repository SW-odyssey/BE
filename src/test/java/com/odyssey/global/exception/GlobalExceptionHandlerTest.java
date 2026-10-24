package com.odyssey.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.response.ApiResult;

/**
 * {@link GlobalExceptionHandler} 가 오류를 GitHub 이슈 #28 의 코드로 바꾸는지,
 * 응답의 enum 이 소문자로 나가는지 확인한다.
 *
 * <p>실제 API 에서는 이런 오류를 일부러 낼 수 없어서, 오류마다 그 예외를 던지는
 * 테스트 전용 컨트롤러({@link TestController}, /test/**)를 둔다. 이 컨트롤러는 이 테스트에서만
 * 등록되고 배포 jar 에는 들어가지 않는다. 테스트 클래스 안의 클래스는 컴포넌트 스캔에서 빠지므로
 * {@link Import} 로 등록한다.
 */
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("서비스가 PLACE_NOT_FOUND 를 던지면 404 와 던질 때 쓴 문구로 응답한다")
	void business() throws Exception {
		// given: PLACE_NOT_FOUND 를 던지는 요청
		MockHttpServletRequestBuilder request = get("/test/business");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 404, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다: 99999");
	}

	@Test
	@DisplayName("쿼리 값의 형식이 틀리면 400 INVALID_INPUT 과 틀린 값을 알려 준다")
	void bindingFailure() throws Exception {
		// given: 있을 수 없는 날짜
		MockHttpServletRequestBuilder request = get("/test/query").param("visitDate", "2026-13-40");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 400, "INVALID_INPUT", "visitDate 값이 올바르지 않습니다: 2026-13-40");
	}

	@Test
	@DisplayName("경로 값의 형식이 틀리면 400 INVALID_INPUT 과 틀린 값을 알려 준다")
	void typeMismatch() throws Exception {
		// given: 숫자 id 자리에 문자
		MockHttpServletRequestBuilder request = get("/test/places/abc");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 400, "INVALID_INPUT", "id 값이 올바르지 않습니다: abc");
	}

	@Test
	@DisplayName("필수 쿼리 값이 빠지면 400 INVALID_INPUT 과 빠진 이름을 알려 준다")
	void missingParameter() throws Exception {
		// given: 필수 값 q 없이 보낸 요청
		MockHttpServletRequestBuilder request = get("/test/required");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 400, "INVALID_INPUT", "q 값이 필요합니다.");
	}

	@Test
	@DisplayName("DB 조회가 실패하면 503 DATA_UNAVAILABLE 로 응답한다")
	void dataAccess() throws Exception {
		// given: DB 연결 실패를 던지는 요청
		MockHttpServletRequestBuilder request = get("/test/db");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 503, "DATA_UNAVAILABLE", "데이터를 일시적으로 불러올 수 없습니다.");
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR 로 응답하고 내부 메시지는 숨긴다")
	void unexpected() throws Exception {
		// given: 내부 메시지("내부 정보")를 담은 예외를 던지는 요청
		MockHttpServletRequestBuilder request = get("/test/boom");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
	}

	@Test
	@DisplayName("Spring 이 5xx 로 처리하는 예외도 INTERNAL_ERROR 로 응답하고 내부 메시지는 숨긴다")
	void springServerError() throws Exception {
		// given: Spring 이 500 으로 처리하는 예외(JSON 변환 실패)를 던지는 요청
		MockHttpServletRequestBuilder request = get("/test/not-writable");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		expectError(result, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
	}

	@Test
	@DisplayName("응답 JSON 의 enum 은 소문자로 나간다 (LATE_NIGHT → late_night)")
	void enumLowercase() throws Exception {
		// given: TimeSlot.LATE_NIGHT 를 담아 응답하는 요청
		MockHttpServletRequestBuilder request = get("/test/enum");

		// when
		ResultActions result = mockMvc.perform(request);

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.slot").value("late_night"));
	}

	private static void expectError(
		ResultActions result,
		int status,
		String code,
		String message) throws Exception {

		result.andExpect(status().is(status))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").isEmpty())
			.andExpect(jsonPath("$.error.code").value(code))
			.andExpect(jsonPath("$.error.message").value(message));
	}

	record Query(LocalDate visitDate) {
	}

	@RestController
	static class TestController {

		@GetMapping("/test/business")
		ApiResult<Void> business() {
			throw new BusinessException(
				CommonErrorCode.PLACE_NOT_FOUND,
				"장소를 찾을 수 없습니다: 99999");
		}

		@GetMapping("/test/query")
		ApiResult<Query> query(@ModelAttribute Query query) {
			return ApiResult.ok(query);
		}

		@GetMapping("/test/places/{id}")
		ApiResult<Long> place(@PathVariable Long id) {
			return ApiResult.ok(id);
		}

		@GetMapping("/test/required")
		ApiResult<String> required(@RequestParam String q) {
			return ApiResult.ok(q);
		}

		@GetMapping("/test/db")
		ApiResult<Void> db() {
			throw new DataAccessResourceFailureException("connection refused");
		}

		@GetMapping("/test/boom")
		ApiResult<Void> boom() {
			throw new IllegalStateException("내부 정보");
		}

		@GetMapping("/test/not-writable")
		ApiResult<Void> notWritable() {
			throw new HttpMessageNotWritableException("내부 정보");
		}

		@GetMapping("/test/enum")
		ApiResult<Map<String, TimeSlot>> slot() {
			return ApiResult.ok(Map.of("slot", TimeSlot.LATE_NIGHT));
		}
	}
}
