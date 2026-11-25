package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.cellvisitprofile.VisitProfile;
import com.odyssey.cellvisitprofile.VisitProfile.VisitBar;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;
import com.odyssey.cellvisitprofile.repository.CellVisitProfileRepository;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.Bar;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.Graph;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.PatternContext;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * {@link V2VisitPatternService} 를 확인한다. 리포지토리는 목으로 대신한다. 막대 규칙은
 * VisitProfile 테스트가 보므로 여기서는 응답 조립과 오류 순서만 본다.
 */
class V2VisitPatternServiceTest {

	private static final String DS = "nov";
	private static final Dataset DATASET =
		new Dataset(DS, "2025-11", "DOMESTIC_20_39", "policy", "reason", "profile");

	private CellVisitProfileRepository repository;
	private V2DatasetResolver datasets;
	private V2VisitPatternService service;

	@BeforeEach
	void setUp() {
		repository = mock(CellVisitProfileRepository.class);
		datasets = mock(V2DatasetResolver.class);
		service = new V2VisitPatternService(repository, datasets);
		given(datasets.require(DS)).willReturn(DATASET);
	}

	private static V2VisitPatternQuery query(Weekday weekday, long cellId) {
		return new V2VisitPatternQuery(DS, weekday, cellId);
	}

	private void givenProfile(Weekday weekday, long cellId, VisitProfile profile) {
		given(repository.find(DS, weekday, cellId)).willReturn(Optional.of(profile));
	}

	private static VisitProfile available(int lunch, int afternoon, int dinner, int lateNight) {
		return new VisitProfile(VisitProfileStatus.AVAILABLE, List.of(
			new VisitBar(TimeSlot.LUNCH, lunch), new VisitBar(TimeSlot.AFTERNOON, afternoon),
			new VisitBar(TimeSlot.DINNER, dinner), new VisitBar(TimeSlot.LATE_NIGHT, lateNight)));
	}

	@Test
	@DisplayName("AVAILABLE 프로필 → 상태와 조회 조건(분석 배치 · 기준 월 · 칸 · 요일)을 준다")
	void availableContext() {
		// given
		givenProfile(Weekday.SUN, 81591938L, available(70, 75, 100, 70));

		// when
		V2VisitPatternResponse response = service.visitPattern(query(Weekday.SUN, 81591938L));

		// then
		assertThat(response.status()).isEqualTo("AVAILABLE");
		assertThat(response.context())
			.isEqualTo(new PatternContext(DS, "2025-11", "81591938", Weekday.SUN));
	}

	@Test
	@DisplayName("AVAILABLE 프로필 → 그래프 설명 값(방문 · 같은 칸 같은 요일 · 100)을 준다")
	void availableGraphDescription() {
		// given
		givenProfile(Weekday.SUN, 10, available(70, 75, 100, 70));

		// when
		Graph graph = service.visitPattern(query(Weekday.SUN, 10)).graph();

		// then
		assertThat(graph.metric()).isEqualTo("visit");
		assertThat(graph.comparison()).isEqualTo("SAME_CELL_SAME_WEEKDAY");
		assertThat(graph.scaleMax()).isEqualTo(100);
	}

	@Test
	@DisplayName("11월 81591938 일요일 70 · 75 · 100 · 70 → 막대를 이 순서로 주고 강조는 저녁이다")
	void availableGraphBars() {
		// given
		givenProfile(Weekday.SUN, 81591938L, available(70, 75, 100, 70));

		// when
		Graph graph = service.visitPattern(query(Weekday.SUN, 81591938L)).graph();

		// then
		assertThat(graph.highlightSlot()).isEqualTo(TimeSlot.DINNER);
		assertThat(graph.bars()).containsExactly(
			new Bar(TimeSlot.LUNCH, 70), new Bar(TimeSlot.AFTERNOON, 75),
			new Bar(TimeSlot.DINNER, 100), new Bar(TimeSlot.LATE_NIGHT, 70));
	}

	private static VisitProfile profile(VisitProfileStatus status, Integer lunch, Integer afternoon,
		Integer dinner, Integer lateNight) {
		return new VisitProfile(status, List.of(
			new VisitBar(TimeSlot.LUNCH, lunch), new VisitBar(TimeSlot.AFTERNOON, afternoon),
			new VisitBar(TimeSlot.DINNER, dinner), new VisitBar(TimeSlot.LATE_NIGHT, lateNight)));
	}

	@Test
	@DisplayName("NO_DISTINCT_PEAK 100 · 85 · 100 · 40 → 막대를 모두 주고 강조 시간대는 null 이다")
	void tiedPeakGraph() {
		// given
		givenProfile(Weekday.WED, 10, profile(VisitProfileStatus.NO_DISTINCT_PEAK, 100, 85, 100, 40));

		// when
		V2VisitPatternResponse response = service.visitPattern(query(Weekday.WED, 10));

		// then
		assertThat(response.status()).isEqualTo("NO_DISTINCT_PEAK");
		assertThat(response.graph().highlightSlot()).isNull();
		assertThat(response.graph().bars()).containsExactly(
			new Bar(TimeSlot.LUNCH, 100), new Bar(TimeSlot.AFTERNOON, 85),
			new Bar(TimeSlot.DINNER, 100), new Bar(TimeSlot.LATE_NIGHT, 40));
	}

	@Test
	@DisplayName("INCOMPLETE_TIME_SLOTS 의 빠진 시간대 → 그 막대 높이는 0 이 아니라 null 이다")
	void incompleteGraphKeepsMissingSlotNull() {
		// given
		givenProfile(Weekday.WED, 10, profile(VisitProfileStatus.INCOMPLETE_TIME_SLOTS, null, 100, 60, null));

		// when
		Graph graph = service.visitPattern(query(Weekday.WED, 10)).graph();

		// then
		assertThat(graph.highlightSlot()).isNull();
		assertThat(graph.bars()).containsExactly(
			new Bar(TimeSlot.LUNCH, null), new Bar(TimeSlot.AFTERNOON, 100),
			new Bar(TimeSlot.DINNER, 60), new Bar(TimeSlot.LATE_NIGHT, null));
	}

	@ParameterizedTest
	@EnumSource(value = VisitProfileStatus.class, names = "AVAILABLE",
		mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("막대가 없는 프로필 → 상태 이름만 주고 graph 는 null 이다")
	void profileWithoutBarsHasNoGraph(VisitProfileStatus status) {
		// given
		givenProfile(Weekday.WED, 10, new VisitProfile(status, List.of()));

		// when
		V2VisitPatternResponse response = service.visitPattern(query(Weekday.WED, 10));

		// then
		assertThat(response.status()).isEqualTo(status.name());
		assertThat(response.graph()).isNull();
	}

	@Test
	@DisplayName("조회한다 → 요청한 배치 · 요일 · 칸 번호로만 프로필을 찾는다")
	void passesConditionToRepository() {
		// given
		givenProfile(Weekday.SAT, 10, available(25, 40, 100, 10));

		// when
		service.visitPattern(query(Weekday.SAT, 10));

		// then
		then(repository).should().find(DS, Weekday.SAT, 10L);
	}

	@Test
	@DisplayName("프로필이 없는 칸 → CELL_NOT_FOUND")
	void missingProfile() {
		// given
		given(repository.find(DS, Weekday.MON, 10L)).willReturn(Optional.empty());

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.visitPattern(query(Weekday.MON, 10)));

		// then
		assertThat(exception.getErrorType()).isEqualTo(V2Error.CELL_NOT_FOUND);
	}

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND 이고 DB 는 조회하지 않는다")
	void unknownDatasetSkipsQuery() {
		// given
		given(datasets.require(DS)).willThrow(new BusinessException(
			V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.visitPattern(query(Weekday.MON, 10)));

		// then
		assertThat(exception.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
		then(repository).should(never()).find(anyString(), any(Weekday.class), anyLong());
	}
}
