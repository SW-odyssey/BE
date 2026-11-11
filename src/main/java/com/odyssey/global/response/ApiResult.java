package com.odyssey.global.response;

import java.util.Objects;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API 응답의 공통 형식.
 *
 * <p>성공
 * <pre>{@code
 * {
 *     "success": true,
 *     "data": { "Status": "Ok" },
 *     "error": null
 * }
 * }</pre>
 *
 * <p>실패
 * <pre>{@code
 * {
 *     "success": false,
 *     "data": null,
 *     "error": {
 *         "code": "NOT_FOUND",
 *         "message": "해당 지역 데이터가 없습니다."
 *     }
 * }
 * }</pre>
 *
 * <p>record 는 "값을 담아 전달하는 것"만이 목적인 클래스다.
 * 괄호 안에 선언한 success / data / error 를 컴포넌트라고 부르며,
 * 컴파일러가 아래 것들을 자동으로 만들어 준다.
 *
 * <ul>
 *   <li>세 값을 모두 받는 생성자</li>
 *   <li>접근자 메서드 - getData() 가 아니라 컴포넌트 이름 그대로 data()</li>
 *   <li>equals / hashCode / toString</li>
 * </ul>
 *
 * <p>모든 필드가 final 이라 한 번 만들면 값을 바꿀 수 없다. 응답 객체는
 * 만들어서 내보내고 끝이므로 이 제약이 오히려 안전하다.
 * Jackson 도 record 를 알고 있어서 별도 설정 없이 JSON 으로 변환된다.
 *
 * <p>{@code <T>} 는 data 에 어떤 타입이든 담기게 하는 제네릭이다.
 * 예) {@code ApiResult<Map<String, String>>}, {@code ApiResult<List<PopulationDto>>}
 */
public record ApiResult<T>(
	@Schema(description = "요청 성공 여부") boolean success,
	@Schema(description = "성공 결과. 실패하면 null") T data,
	@Schema(description = "오류 정보. 성공하면 null") ApiError error) {

	public static <T> ApiResult<T> ok(T data) {
		return new ApiResult<>(true, data, null);
	}

	public static <T> ApiResult<T> fail(String code, String message) {
		return new ApiResult<>(false, null, new ApiError(code, message));
	}

	/* record 타입이 기본으로 만들어주는 것들입니다.
	public ApiResult(boolean success, T data, ApiError error) {
		this.success = success;
		this.data = data;
		this.error = error;
	}

	@Override
	public boolean success() {
		return success;
	}

	@Override
	public T data() {
		return data;
	}

	@Override
	public ApiError error() {
		return error;
	}

	@Override
	public boolean equals(Object object) {
		if (object == null || getClass() != object.getClass()) return false;
		ApiResult<?> apiResult = (ApiResult<?>) object;
		return success == apiResult.success && Objects.equals(data, apiResult.data) && Objects.equals(error, apiResult.error);
	}

	@Override
	public int hashCode() {
		return Objects.hash(success, data, error);
	}

	@Override
	public String toString() {
		return "ApiResult{" +
				"success=" + success +
				", data=" + data +
				", error=" + error +
				'}';
	}
	*/
}
