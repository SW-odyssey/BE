package com.odyssey.recommendation.v2;

import org.springframework.http.HttpStatus;

import com.odyssey.global.exception.ErrorType;

/** 추천 v2 API 에서만 쓰는 오류 코드. 응답의 error.code 는 상수 이름이다. */
public enum V2Error implements ErrorType {

	// 요청한 분석 배치가 없거나 공개되지 않았다. 다른 달의 배치로 대신하지 않는다
	DATASET_NOT_FOUND(HttpStatus.NOT_FOUND);

	private final HttpStatus status;

	V2Error(HttpStatus status) {
		this.status = status;
	}

	@Override
	public HttpStatus getStatus() {
		return status;
	}
}
