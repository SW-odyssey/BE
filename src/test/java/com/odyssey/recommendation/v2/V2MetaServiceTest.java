package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;

/**
 * {@link V2MetaService} 를 확인한다. 프론트는 meta 가 준 값만 골라 보내므로,
 * meta 가 준 값을 {@link V2Query} 가 모두 받아들이는지가 이 API 의 약속이다.
 */
class V2MetaServiceTest {

	private static final String POLICY = "visit75-band300-v2-split";
	private static final String REASON = "visit75-no-worker-v2-split";
	private static final String PROFILE = "visit-only-relative100-step5-v2-split";

	private CellInsightRepository repo;
	private V2MetaService service;

	@BeforeEach
	void setup() {
		repo = mock(CellInsightRepository.class);
		service = new V2MetaService(repo);
	}

	@Test
	@DisplayName("지원하는 데이터셋이 여럿이다 → 리포지토리가 준 순서대로 id · 기준월 · 대상을 준다")
	void listsSupportedDatasets() {
		// given
		when(repo.datasets()).thenReturn(List.of(
			new Dataset("nov", "2025-11", "DOMESTIC_20_39", POLICY, REASON, PROFILE),
			new Dataset("oct", "2025-10", "DOMESTIC_20_39", POLICY, REASON, PROFILE)));

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.datasets()).containsExactly(
			new DatasetOption("nov", "2025-11", "DOMESTIC_20_39"),
			new DatasetOption("oct", "2025-10", "DOMESTIC_20_39"));
	}

	@Test
	@DisplayName("서버가 모르는 버전의 데이터셋이 섞여 있다 → 지원하는 것만 준다")
	void hidesUnsupportedDatasets() {
		// given
		when(repo.datasets()).thenReturn(List.of(
			new Dataset("old", "2025-09", "DOMESTIC_20_39", "other-policy", REASON, PROFILE),
			new Dataset("nov", "2025-11", "DOMESTIC_20_39", POLICY, REASON, PROFILE)));

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.datasets()).extracting(DatasetOption::id).containsExactly("nov");
	}

	@Test
	@DisplayName("공개된 데이터셋이 없다 → 데이터셋만 빈 목록이고 나머지 선택지는 그대로 준다")
	void noDatasets() {
		// given
		when(repo.datasets()).thenReturn(List.of());

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.datasets()).isEmpty();
		assertThat(response.weekdays()).hasSize(7);
		assertThat(response.slots()).hasSize(4);
	}

	@Test
	@DisplayName("고정 선택지 → 요일 7개 · 시간대 4개 · 카테고리 · 종류를 약속한 값과 순서로 준다")
	void fixedOptions() {
		// given
		when(repo.datasets()).thenReturn(List.of());

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.weekdays()).containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
		assertThat(response.slots()).containsExactly("lunch", "afternoon", "dinner", "late_night");
		assertThat(response.categories()).containsExactly("food", "cafe", "all");
		assertThat(response.kinds()).containsExactly("korean", "bar", "other", "cafe", "all");
	}

	@Test
	@DisplayName("meta 가 준 요일 · 시간대 · 카테고리 · 종류 → V2Query 가 모두 받아들인다")
	void metaValuesAreAcceptedByQuery() {
		// given
		when(repo.datasets()).thenReturn(List.of());
		V2MetaResponse meta = service.meta();

		// when / then
		for (String weekday : meta.weekdays()) {
			assertThatCode(() -> new V2Query("nov", weekday, "dinner", 37.5, 127.0, "all", "all", ""))
				.doesNotThrowAnyException();
		}
		for (String slot : meta.slots()) {
			assertThatCode(() -> new V2Query("nov", "mon", slot, 37.5, 127.0, "all", "all", ""))
				.doesNotThrowAnyException();
		}
		for (String category : meta.categories()) {
			assertThatCode(() -> new V2Query("nov", "mon", "dinner", 37.5, 127.0, category, "all", ""))
				.doesNotThrowAnyException();
		}
		for (String kind : meta.kinds()) {
			assertThatCode(() -> new V2Query("nov", "mon", "dinner", 37.5, 127.0, "all", kind, ""))
				.doesNotThrowAnyException();
		}
	}
}
