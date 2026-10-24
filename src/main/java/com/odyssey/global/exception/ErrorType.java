package com.odyssey.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드의 공통 모양. 응답의 error.code 로 나가고, 프론트는 이 값으로 분기한다.
 *
 * <p>지금은 오류 코드를 모두 {@link CommonErrorCode} 에 모은다.
 * 오류를 추가할 때 이 인터페이스와 {@link GlobalExceptionHandler} 는 고치지 않는다.
 *
 * <p>enum 이 구현하면 {@link #name()} 은 enum 상수 이름이 그대로 쓰인다.
 */
public interface ErrorType {

	/**
	 * @return 응답의 error.code (예: PLACE_NOT_FOUND)
	 */
	String name();

	/**
	 * @return 이 오류에 쓸 HTTP 상태 코드
	 */
	HttpStatus getStatus();
}
