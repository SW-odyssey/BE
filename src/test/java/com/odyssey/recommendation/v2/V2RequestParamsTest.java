package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.odyssey.cellcondition.entity.Weekday;
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

	@ParameterizedTest
	@ValueSource(strings = {"nov", "ds_2026-10", "A-1"})
	@DisplayName("영문 · 숫자 · _ · - 로 된 datasetId → 그대로 돌려준다")
	void validDatasetId(String datasetId) {
		assertThat(V2RequestParams.requireDatasetId(datasetId)).isEqualTo(datasetId);
	}

	@Test
	@DisplayName("datasetId 가 80자다 → 그대로 돌려준다")
	void eightyCharacterDatasetId() {
		String datasetId = "a".repeat(80);

		assertThat(V2RequestParams.requireDatasetId(datasetId)).isEqualTo(datasetId);
	}

	@Test
	@DisplayName("datasetId 가 null 이다 → INVALID_INPUT")
	void nullDatasetId() {
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.requireDatasetId(null));

		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception.getMessage()).isEqualTo("meta에서 제공한 datasetId가 필요합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "a b", "a/b", "한글"})
	@DisplayName("비어 있거나 허용하지 않는 문자가 든 datasetId → INVALID_INPUT")
	void invalidDatasetId(String datasetId) {
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.requireDatasetId(datasetId));

		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("datasetId 가 81자다 → INVALID_INPUT")
	void tooLongDatasetId() {
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.requireDatasetId("a".repeat(81)));

		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("enum 요청 값이 소문자 이름이다 → 해당 enum 을 준다")
	void requiredEnum() {
		assertThat(V2RequestParams.required(Weekday.class, "weekday", "sun"))
			.isEqualTo(Weekday.SUN);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  "})
	@DisplayName("꼭 있어야 하는 enum 값이 비어 있다 → \"이름 값이 필요합니다.\"")
	void requiredEnumBlank(String value) {
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.required(Weekday.class, "weekday", value));

		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception.getMessage()).isEqualTo("weekday 값이 필요합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"SUN", "monday", "mon_thu"})
	@DisplayName("enum 요청 값이 허용하지 않는 값이다 → INVALID_INPUT")
	void requiredEnumUnknown(String value) {
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> V2RequestParams.required(Weekday.class, "weekday", value));

		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}
}
