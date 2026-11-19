package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;

import com.odyssey.global.api.ApiEnums;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** v2 API 가 받는 쿼리 파라미터의 공통 규칙. 키 규칙과, 여러 API 가 함께 쓰는 값 형식을 둔다. */
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

	/**
	 * datasetId 의 형식을 확인한다. 있는 배치인지는 확인하지 않는다.
	 *
	 * @return 받은 값 그대로
	 * @throws BusinessException INVALID_INPUT. 없거나 영문·숫자·_·- 가 아니거나 80자를 넘는 경우
	 */
	static String requireDatasetId(String datasetId) {
		if (datasetId == null || !datasetId.matches("[a-zA-Z0-9_-]{1,80}")) {
			throw new BusinessException(
				CommonErrorCode.INVALID_INPUT, "meta에서 제공한 datasetId가 필요합니다.");
		}
		return datasetId;
	}

	/**
	 * 꼭 있어야 하는 enum 요청 값을 바꾼다. 비어 있으면 생략한 것으로 보지 않고 오류로 본다.
	 *
	 * @throws BusinessException INVALID_INPUT. 비어 있거나 허용하지 않는 값인 경우
	 */
	static <E extends Enum<E>> E required(Class<E> type, String name, String value) {
		return ApiEnums.parse(type, name, value, (String) null)
			.orElseThrow(() -> new BusinessException(
				CommonErrorCode.INVALID_INPUT, name + " 값이 필요합니다."));
	}
}
