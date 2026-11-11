package com.odyssey.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 에러 응답의 error 필드에 들어가는 값.
 *
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
 */
public record ApiError(
	@Schema(description = "오류 코드", example = "INVALID_INPUT") String code,
	@Schema(description = "오류 원인과 입력 수정 안내", example = "since 는 true 또는 false 입니다")
	String message) {
}
