package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** v2 API 가 받는 쿼리 파라미터의 키 규칙. 값의 형식은 각 API 가 따로 검증한다. */
final class V2RequestParams {

	private V2RequestParams() {
	}

	/**
	 * 허용한 키만, 키마다 정확히 한 번씩 들어왔는지 확인한다.
	 *
	 * @param params      요청의 쿼리 파라미터 전체
	 * @param allowedKeys 이 API 가 받는 키
	 * @throws BusinessException INVALID_INPUT. 허용하지 않은 키이거나 같은 키가 여러 번 들어온 경우
	 */
	static void check(MultiValueMap<String, String> params, Set<String> allowedKeys) {
		params.forEach((key, values) -> {
			if (!allowedKeys.contains(key) || values.size() != 1) {
				throw new BusinessException(
					CommonErrorCode.INVALID_INPUT, "지원하지 않거나 중복된 요청 조건: " + key);
			}
		});
	}
}
