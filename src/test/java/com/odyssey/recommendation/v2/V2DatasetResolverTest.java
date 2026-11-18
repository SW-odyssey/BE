package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/** {@link V2DatasetResolver} 의 배치 선택 규칙을 확인한다. 리포지토리는 목으로 대신한다. */
class V2DatasetResolverTest {

	private static final String POLICY = "visit75-band300-v2-split";
	private static final String REASON = "visit75-no-worker-v2-split";
	private static final String PROFILE = "visit-only-relative100-step5-v2-split";

	private RecommendationDatasetRepository repository;
	private V2DatasetResolver resolver;

	@BeforeEach
	void setUp() {
		repository = mock(RecommendationDatasetRepository.class);
		resolver = new V2DatasetResolver(repository);
	}

	private static Dataset supported(String id, String month) {
		return new Dataset(id, month, "DOMESTIC_20_39", POLICY, REASON, PROFILE);
	}

	@Test
	@DisplayName("지원하는 공개 배치의 ID → 그 배치를 준다")
	void requireSupported() {
		// given
		Dataset november = supported("nov", "2025-11");
		given(repository.findPublished()).willReturn(List.of(november));

		// when
		Dataset found = resolver.require("nov");

		// then
		assertThat(found).isEqualTo(november);
	}

	@Test
	@DisplayName("배치가 하나도 없다 → 404 DATASET_NOT_FOUND")
	void requireWhenNoDataset() {
		// given
		given(repository.findPublished()).willReturn(List.of());

		// when
		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> resolver.require("nov"));

		// then
		assertThat(thrown.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
	}

	@Test
	@DisplayName("요청한 달의 배치가 없고 다른 달만 있다 → 다른 달로 대신하지 않고 404 DATASET_NOT_FOUND")
	void requireNeverFallsBackToAnotherMonth() {
		// given: 11월 배치만 있는데 12월을 요청한다
		given(repository.findPublished()).willReturn(List.of(supported("nov", "2025-11")));

		// when
		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> resolver.require("dec"));

		// then
		assertThat(thrown.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
	}

	@ParameterizedTest
	@CsvSource({
		"other-policy, " + REASON + ", " + PROFILE,
		POLICY + ", other-reason, " + PROFILE,
		POLICY + ", " + REASON + ", other-profile"
	})
	@DisplayName("정책 버전 셋 중 하나라도 지원하지 않는다 → 503 DATA_UNAVAILABLE")
	void requireUnsupportedVersion(String policy, String reason, String profile) {
		// given
		Dataset unsupported = new Dataset(
			"nov", "2025-11", "DOMESTIC_20_39", policy, reason, profile);
		given(repository.findPublished()).willReturn(List.of(unsupported));

		// when
		BusinessException thrown = catchThrowableOfType(BusinessException.class,
			() -> resolver.require("nov"));

		// then
		assertThat(thrown.getErrorType()).isEqualTo(CommonErrorCode.DATA_UNAVAILABLE);
	}

	@Test
	@DisplayName("지원하지 않는 배치가 섞여 있다 → 지원하는 배치만 받은 순서대로 준다")
	void findSupportedOnlySupported() {
		// given
		Dataset december = supported("dec", "2025-12");
		Dataset unsupported = new Dataset("nov", "2025-11", "DOMESTIC_20_39",
			"old-policy", REASON, PROFILE);
		Dataset october = supported("oct", "2025-10");
		given(repository.findPublished()).willReturn(List.of(december, unsupported, october));

		// when
		List<Dataset> found = resolver.findSupported();

		// then
		assertThat(found).containsExactly(december, october);
	}

	@Test
	@DisplayName("공개된 배치가 없다 → 지원하는 배치도 빈 목록이다")
	void findSupportedEmpty() {
		// given
		given(repository.findPublished()).willReturn(List.of());

		// when
		List<Dataset> found = resolver.findSupported();

		// then
		assertThat(found).isEmpty();
	}
}
