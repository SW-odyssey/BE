package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.cellinsight.repository.CellInsightRepository.ProfileRow;
import com.odyssey.cellinsight.repository.CellInsightRepository.VisitBar;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.Bar;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.PatternContext;

/**
 * {@link V2VisitPatternService} 를 확인한다. 리포지토리는 목으로 대신한다.
 *
 * <p>방문 패턴 그래프는 요일 단위이고 시간대와 무관하다. AVAILABLE 일 때만 그래프를 만들고,
 * 공개된 프로필이 깨져 있으면 조용히 넘기지 않고 서버 오류로 드러낸다.
 */
class V2VisitPatternServiceTest {

	private static final String DS = "nov";

	private CellInsightRepository repo;
	private V2VisitPatternService service;

	@BeforeEach
	void setup() {
		repo = mock(CellInsightRepository.class);
		when(repo.datasets()).thenReturn(List.of(new Dataset(DS, "2025-11", "DOMESTIC_20_39",
			"visit75-band300-v2-split", "visit75-no-worker-v2-split",
			"visit-only-relative100-step5-v2-split")));
		service = new V2VisitPatternService(repo, new V2DatasetResolver(repo));
	}

	private void profile(String day, long cellId, String status, VisitBar... bars) {
		when(repo.profile(DS, day, cellId)).thenReturn(Optional.of(new ProfileRow(status, List.of(bars))));
	}

	@Test
	@DisplayName("AVAILABLE 프로필 → 그래프 설명 값과 막대를 받은 순서대로, 가장 높은 막대를 강조로 준다")
	void availableGraph() {
		// given
		profile("sat", 10, "AVAILABLE", new VisitBar("lunch", 25), new VisitBar("afternoon", 40),
			new VisitBar("dinner", 100), new VisitBar("late_night", 10));

		// when
		V2VisitPatternResponse response = service.profile(DS, "sat", 10);

		// then
		assertThat(response.status()).isEqualTo("AVAILABLE");
		assertThat(response.context()).isEqualTo(new PatternContext(DS, "2025-11", "10", "sat"));
		assertThat(response.graph().metric()).isEqualTo("visit");
		assertThat(response.graph().comparison()).isEqualTo("SAME_CELL_SAME_WEEKDAY");
		assertThat(response.graph().scaleMax()).isEqualTo(100);
		assertThat(response.graph().highlightSlot()).isEqualTo("dinner");
		assertThat(response.graph().bars()).containsExactly(new Bar("lunch", 25), new Bar("afternoon", 40),
			new Bar("dinner", 100), new Bar("late_night", 10));
	}

	@ParameterizedTest
	@ValueSource(strings = {"INCOMPLETE_TIME_SLOTS", "NO_POSITIVE_VISITS", "NO_DISTINCT_PEAK"})
	@DisplayName("AVAILABLE 이 아닌 프로필 → 상태만 주고 그래프는 만들지 않는다")
	void nonAvailableHasNoGraph(String status) {
		// given
		profile("wed", 10, status);

		// when
		V2VisitPatternResponse response = service.profile(DS, "wed", 10);

		// then
		assertThat(response.status()).isEqualTo(status);
		assertThat(response.graph()).isNull();
	}

	@Test
	@DisplayName("프로필이 없는 칸 → CELL_NOT_FOUND")
	void missingProfile() {
		// given
		when(repo.profile(DS, "mon", 10L)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.profile(DS, "mon", 10)).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(V2Error.CELL_NOT_FOUND));
	}

	@Test
	@DisplayName("공개된 AVAILABLE 프로필이 깨져 있다(막대 3개 · 100 이 둘 · 100 이 없음) → 서버 오류로 드러낸다")
	void corruptPublishedProfileFails() {
		// given / when / then: 막대가 3개
		profile("mon", 1, "AVAILABLE", new VisitBar("lunch", 100), new VisitBar("afternoon", 50),
			new VisitBar("dinner", 20));
		assertThatThrownBy(() -> service.profile(DS, "mon", 1)).isInstanceOf(IllegalStateException.class);

		// 100 이 둘
		profile("mon", 2, "AVAILABLE", new VisitBar("lunch", 100), new VisitBar("afternoon", 100),
			new VisitBar("dinner", 20), new VisitBar("late_night", 0));
		assertThatThrownBy(() -> service.profile(DS, "mon", 2)).isInstanceOf(IllegalStateException.class);

		// 100 이 없음
		profile("mon", 3, "AVAILABLE", new VisitBar("lunch", 90), new VisitBar("afternoon", 50),
			new VisitBar("dinner", 20), new VisitBar("late_night", 0));
		assertThatThrownBy(() -> service.profile(DS, "mon", 3)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -5})
	@DisplayName("칸 번호가 0 이하 → INVALID_INPUT, DB 는 조회하지 않는다")
	void invalidCellId(long cellId) {
		// when / then
		assertThatThrownBy(() -> service.profile(DS, "mon", cellId)).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT));
		verify(repo, never()).profile(anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("요일을 대문자로 보낸다 → 소문자로 조회하고 조건도 소문자로 돌려준다")
	void normalizesWeekday() {
		// given
		profile("sat", 10, "NO_POSITIVE_VISITS");

		// when
		V2VisitPatternResponse response = service.profile(DS, "SAT", 10);

		// then
		assertThat(response.context().weekday()).isEqualTo("sat");
	}

	@Test
	@DisplayName("허용하지 않는 요일 → INVALID_INPUT, DB 는 조회하지 않는다")
	void invalidWeekday() {
		// when / then
		assertThatThrownBy(() -> service.profile(DS, "mon_thu", 10)).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT));
		verify(repo, never()).profile(anyString(), anyString(), anyLong());
	}

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND, DB 는 조회하지 않는다")
	void unknownDataset() {
		// when / then
		assertThatThrownBy(() -> service.profile("unknown", "mon", 10)).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND));
		verify(repo, never()).profile(anyString(), anyString(), anyLong());
	}
}
