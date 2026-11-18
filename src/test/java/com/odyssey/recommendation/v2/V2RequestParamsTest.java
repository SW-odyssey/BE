package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

class V2RequestParamsTest {

	@Test
	@DisplayName("허용한 키가 한 번씩만 들어왔다 → 통과한다")
	void allowedKeysPass() {
		// given
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("datasetId", "nov");
		params.add("weekday", "mon");

		// when & then
		assertThatCode(() -> V2RequestParams.check(params, Set.of("datasetId", "weekday")))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("받는 키가 없는 API 에 키를 보내지 않았다 → 통과한다")
	void emptyParamsPass() {
		// given
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

		// when & then
		assertThatCode(() -> V2RequestParams.check(params, Set.of())).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("허용하지 않은 키가 있다 → 400 INVALID_INPUT 이고 문구에 그 키를 담는다")
	void unsupportedKey() {
		// given
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("radiusMeters", "300");

		// when
		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.check(params, Set.of("datasetId")));

		// then
		assertThat(thrown.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(thrown).hasMessage("지원하지 않거나 중복된 요청 조건: radiusMeters");
	}

	@Test
	@DisplayName("같은 키가 두 번 들어왔다 → 400 INVALID_INPUT")
	void duplicatedKey() {
		// given
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("datasetId", "nov");
		params.add("datasetId", "dec");

		// when
		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.check(params, Set.of("datasetId")));

		// then
		assertThat(thrown.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(thrown).hasMessage("지원하지 않거나 중복된 요청 조건: datasetId");
	}
}
