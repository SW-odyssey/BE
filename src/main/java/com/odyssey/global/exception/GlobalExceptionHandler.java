package com.odyssey.global.exception;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.odyssey.global.response.ApiResult;

import lombok.extern.slf4j.Slf4j;

// 컨트롤러에서 터진 예외를 한곳에서 받아 ApiResult 형식으로 변경
// 오류 코드는 ErrorType 을 구현한 enum (공통은 CommonErrorCode), 프론트 동작은 GitHub 이슈 #28
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	// 지정 예외 방식. 상태 코드는 오류 코드가 정한다
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
		return fail(e.getErrorType(), e.getMessage());
	}

	// 503(SERVICE_UNAVAILABLE)
	// DB 연결 끊김, 쿼리 실패. 잠시 뒤 다시 시도하면 될 수 있는 오류
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ApiResult<Void>> handleDataAccess(DataAccessException e) {
		log.warn("DB 조회 실패", e);
		return fail(CommonErrorCode.DATA_UNAVAILABLE, "데이터를 일시적으로 불러올 수 없습니다.");
	}

	// 500(INTERNAL_SERVER_ERROR)
	// 위에서 안 잡힌 나머지 전부
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception e) {
		// 원인은 로그에만 남긴다
		log.error("처리되지 않은 예외", e);

		// 응답 메시지는 고정 (내부 정보 노출 방지)
		return fail(CommonErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다.");
	}

	// 400(BAD_REQUEST)
	// @Valid 검증 실패, @ModelAttribute 로 받은 값의 형식 오류 (예: visitDate=2026-13-40)
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {

		// 오류가 여러 개여도 첫 번째만 쓴다
		FieldError fieldError = exception
			.getBindingResult()
			.getFieldError();

		// 필드 오류가 없을 때 쓸 기본 문구
		String message = "잘못된 요청입니다.";
		if (fieldError != null && fieldError.isBindingFailure()) {
			// 형식 변환 실패는 기본 문구가 영어 내부 메시지라 직접 만든다
			message = invalidValue(fieldError.getField(), fieldError.getRejectedValue());
		} else if (fieldError != null) {
			// 예) size: 1 이상이어야 합니다
			message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
		}

		return failAsObject(CommonErrorCode.INVALID_INPUT, message);
	}

	// 400(BAD_REQUEST)
	// @RequestParam, @PathVariable 의 형식 오류 (예: /places/abc)
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
		TypeMismatchException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {

		String name = exception instanceof MethodArgumentTypeMismatchException mismatch
			? mismatch.getName()
			: exception.getPropertyName();
		return failAsObject(
			CommonErrorCode.INVALID_INPUT,
			invalidValue(name, exception.getValue()));
	}

	// 400(BAD_REQUEST)
	// 필수 @RequestParam 이 빠짐
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
		MissingServletRequestParameterException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request) {

		return failAsObject(
			CommonErrorCode.INVALID_INPUT,
			exception.getParameterName() + " 값이 필요합니다.");
	}

	// 404, 405 처럼 Spring 이 자체 처리하는 예외를 ApiResult 형식으로
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
		Exception exception,
		Object body,
		HttpHeaders headers,
		HttpStatusCode statusCode,
		WebRequest request) {

		// 위에서 따로 다루지 않은 400 도 프론트가 한 코드로 처리하게 INVALID_INPUT 으로 묶는다
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			return failAsObject(CommonErrorCode.INVALID_INPUT, "잘못된 요청입니다.");
		}

		// 나머지는 NOT_FOUND 같은 상태 이름을 code 로 쓴다
		ApiResult<Void> result = ApiResult.fail(
			HttpStatus.valueOf(statusCode.value()).name(),
			exception.getMessage());

		return new ResponseEntity<>(result, headers, statusCode);
	}

	// 예) slot 값이 올바르지 않습니다: evening
	private static String invalidValue(String name, Object value) {
		return name + " 값이 올바르지 않습니다: " + value;
	}

	private static ResponseEntity<ApiResult<Void>> fail(ErrorType errorType, String message) {
		ApiResult<Void> result = ApiResult.fail(errorType.name(), message);
		return new ResponseEntity<>(result, errorType.getStatus());
	}

	// ResponseEntityExceptionHandler 의 메서드는 ResponseEntity<Object> 를 돌려줘야 한다
	private static ResponseEntity<Object> failAsObject(ErrorType errorType, String message) {
		ApiResult<Void> result = ApiResult.fail(errorType.name(), message);
		return new ResponseEntity<>(result, errorType.getStatus());
	}
}
