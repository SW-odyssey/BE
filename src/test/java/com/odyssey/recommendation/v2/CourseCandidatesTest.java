package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/** {@link CourseCandidates} 가 자리별 하한 중 가장 낮은 값으로 시간대마다 한 번씩 읽는지 확인한다. */
class CourseCandidatesTest {

	private final CellConditionRepository repository = mock(CellConditionRepository.class);
	private final V2DatasetResolver datasets = mock(V2DatasetResolver.class);
	private final CourseCandidates courseCandidates = new CourseCandidates(repository, datasets);

	@BeforeEach
	void dataset() {
		given(datasets.require("nov")).willReturn(
			new Dataset("nov", "2025-11", "DOMESTIC_20_39", "policy", "reason", "profile"));
	}

	private static Candidate candidate(long id, double percentile) {
		return new Candidate(new Place(id, "매장" + id, id, StoreKind.PLAY, 37.5660, 126.9900, null, "place-" + id),
			"m" + id, percentile);
	}

	@Test
	@DisplayName("코스 시간대를 읽음 → 시간대마다 방문 백분위 60 으로 한 번씩 조회한다")
	void readsEachSlotWithCourseMinimum() {
		// given
		Candidate lunch = candidate(1, 80);
		Candidate afternoon = candidate(2, 61);
		given(repository.candidates("nov", Weekday.FRI, TimeSlot.LUNCH, 60.0)).willReturn(List.of(lunch));
		given(repository.candidates("nov", Weekday.FRI, TimeSlot.AFTERNOON, 60.0)).willReturn(List.of(afternoon));

		// when
		CandidatePool pool = courseCandidates.load("nov", Weekday.FRI, TimeSlot.LUNCH,
			List.of(TimeSlot.LUNCH, TimeSlot.AFTERNOON));

		// then
		then(repository).should().candidates("nov", Weekday.FRI, TimeSlot.LUNCH, 60.0);
		then(repository).should().candidates("nov", Weekday.FRI, TimeSlot.AFTERNOON, 60.0);
		verifyNoMoreInteractions(repository);
		assertThat(pool.bySlot()).containsOnlyKeys(TimeSlot.LUNCH, TimeSlot.AFTERNOON);
		assertThat(pool.bySlot().get(TimeSlot.AFTERNOON)).containsExactly(afternoon);
		assertThat(pool.context()).isEqualTo(new V2Context("nov", "2025-11", Weekday.FRI, TimeSlot.LUNCH));
	}

	@Test
	@DisplayName("읽을 시간대가 없음(심야 시작에 심야를 고르지 않음) → 조회하지 않고 빈 후보를 준다")
	void readsNothingWithoutSlots() {
		// when
		CandidatePool pool = courseCandidates.load("nov", Weekday.FRI, TimeSlot.LATE_NIGHT, List.of());

		// then
		verifyNoMoreInteractions(repository);
		assertThat(pool.bySlot()).isEmpty();
	}
}
