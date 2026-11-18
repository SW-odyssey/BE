package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

	/** 데이터셋과 무관한 고정 선택지를 꺼내는 도우미. 파라미터 테스트의 입력으로 쓴다. */
	private static V2MetaResponse fixedMeta() {
		CellInsightRepository empty = mock(CellInsightRepository.class);
		given(empty.datasets()).willReturn(List.of());
		return new V2MetaService(empty).meta();
	}

	static Stream<String> weekdays() {
		return fixedMeta().weekdays().stream();
	}

	static Stream<String> slots() {
		return fixedMeta().slots().stream();
	}

	static Stream<String> categories() {
		return fixedMeta().categories().stream();
	}

	static Stream<String> kinds() {
		return fixedMeta().kinds().stream();
	}

	@Test
	@DisplayName("지원하는 데이터셋이 여럿이다 → 리포지토리가 준 순서대로 id · 기준월 · 대상을 준다")
	void listsSupportedDatasets() {
		// given
		given(repo.datasets()).willReturn(List.of(
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
		given(repo.datasets()).willReturn(List.of(
			new Dataset("old", "2025-09", "DOMESTIC_20_39", "other-policy", REASON, PROFILE),
			new Dataset("nov", "2025-11", "DOMESTIC_20_39", POLICY, REASON, PROFILE)));

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.datasets()).extracting(DatasetOption::id).containsExactly("nov");
	}

	@Test
	@DisplayName("공개된 데이터셋이 없다 → 데이터셋 목록만 비어 있다")
	void noDatasets() {
		// given
		given(repo.datasets()).willReturn(List.of());

		// when
		V2MetaResponse response = service.meta();

		// then
		assertThat(response.datasets()).isEmpty();
	}

	@Test
	@DisplayName("조회하면 → 요일 7개 · 시간대 4개 · 카테고리 · 종류를 약속한 값과 순서로 준다")
	void fixedOptions() {
		// when
		V2MetaResponse response = fixedMeta();

		// then
		assertThat(response.weekdays())
			.containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
		assertThat(response.slots()).containsExactly("lunch", "afternoon", "dinner", "late_night");
		assertThat(response.categories()).containsExactly("food", "cafe", "all");
		assertThat(response.kinds()).containsExactly("korean", "bar", "other", "cafe", "all");
	}

	@ParameterizedTest
	@MethodSource("weekdays")
	@DisplayName("meta 가 준 요일 → V2Query 가 받아들인다")
	void queryAcceptsMetaWeekday(String weekday) {
		// when
		V2Query query = new V2Query("nov", weekday, "dinner", 37.5, 127.0, "all", "all", "");

		// then
		assertThat(query.weekday()).isEqualTo(weekday);
	}

	@ParameterizedTest
	@MethodSource("slots")
	@DisplayName("meta 가 준 시간대 → V2Query 가 받아들인다")
	void queryAcceptsMetaSlot(String slot) {
		// when
		V2Query query = new V2Query("nov", "mon", slot, 37.5, 127.0, "all", "all", "");

		// then
		assertThat(query.slot()).isEqualTo(slot);
	}

	@ParameterizedTest
	@MethodSource("categories")
	@DisplayName("meta 가 준 카테고리 → V2Query 가 받아들인다")
	void queryAcceptsMetaCategory(String category) {
		// when
		V2Query query = new V2Query("nov", "mon", "dinner", 37.5, 127.0, category, "all", "");

		// then
		assertThat(query).isNotNull();
	}

	@ParameterizedTest
	@MethodSource("kinds")
	@DisplayName("meta 가 준 종류 → V2Query 가 받아들인다")
	void queryAcceptsMetaKind(String kind) {
		// when
		V2Query query = new V2Query("nov", "mon", "dinner", 37.5, 127.0, "all", kind, "");

		// then
		assertThat(query).isNotNull();
	}
}
