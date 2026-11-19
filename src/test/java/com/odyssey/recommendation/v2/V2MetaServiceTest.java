package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * {@link V2MetaService} 를 확인한다. 선택지의 순서는 프론트 목(meta.json)과의 약속이라 목록 전체를 고정한다.
 */
class V2MetaServiceTest {

	private V2DatasetResolver datasets;
	private V2MetaService service;

	@BeforeEach
	void setUp() {
		datasets = mock(V2DatasetResolver.class);
		service = new V2MetaService(datasets);
		given(datasets.findSupported()).willReturn(List.of());
	}

	@Test
	@DisplayName("지원하는 배치가 있다 → id, 기준월, 대상을 그대로 받은 순서대로 준다")
	void datasetOptions() {
		// given
		given(datasets.findSupported()).willReturn(List.of(
			new Dataset("2025-12-a", "2025-12", "DOMESTIC_20_39", "p", "r", "v"),
			new Dataset("2025-11-b", "2025-11", "DOMESTIC_20_39", "p", "r", "v")));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.datasets()).containsExactly(
			new DatasetOption("2025-12-a", "2025-12", "DOMESTIC_20_39"),
			new DatasetOption("2025-11-b", "2025-11", "DOMESTIC_20_39"));
	}

	@Test
	@DisplayName("지원하는 배치가 없다 → 배치는 빈 목록이고 고정 선택지는 그대로 준다")
	void emptyDatasetsKeepFixedChoices() {
		// given: 적재가 끝나기 전의 빈 DB

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.datasets()).isEmpty();
		assertThat(meta.weekdays()).isNotEmpty();
	}

	@Test
	@DisplayName("요일 선택지 → 월요일부터 일요일 순서다")
	void weekdays() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.weekdays())
			.containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
	}

	@Test
	@DisplayName("시간대 선택지 → 점심, 오후, 저녁, 심야 순서다")
	void slots() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.slots()).containsExactly("lunch", "afternoon", "dinner", "late_night");
	}

	@Test
	@DisplayName("대분류 선택지 → 음식점, 카페, 전체 순서다")
	void categories() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.categories()).containsExactly("food", "cafe", "all");
	}

	@Test
	@DisplayName("가게 분류 선택지 → enum 선언 순서가 아니라 화면 순서(한식, 술집, 기타, 카페, 전체)다")
	void kinds() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.kinds()).containsExactly("korean", "bar", "other", "cafe", "all");
	}
}
