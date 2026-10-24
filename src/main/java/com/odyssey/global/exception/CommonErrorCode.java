package com.odyssey.global.exception;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 오류를 추가할 때는 여기에 한 줄만 넣는다.
 *
 * <p>목록과 프론트 동작은 GitHub 이슈 #28 (API 공통 규약) 이 기준이다.
 */
public enum CommonErrorCode implements ErrorType {

	// 모르는 값, 날짜 범위 밖, 형식 오류
	INVALID_INPUT(HttpStatus.BAD_REQUEST),

	// 결과가 최대 개수(200곳)를 넘음. 프론트는 지역이나 조건을 좁히라고 안내한다
	QUERY_SCOPE_TOO_WIDE(HttpStatus.BAD_REQUEST),

	// 없는 장소 id 이거나 폐업(active=false). 프론트는 목록으로 돌아간다
	PLACE_NOT_FOUND(HttpStatus.NOT_FOUND),

	// DB 조회 실패
	DATA_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

	// 그 밖의 서버 오류
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	CommonErrorCode(HttpStatus status) {
		this.status = status;
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}
}
