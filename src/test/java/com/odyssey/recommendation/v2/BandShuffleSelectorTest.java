package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.BeamSelectorTest.CONTEXT;
import static com.odyssey.recommendation.v2.BeamSelectorTest.SLOTS;
import static com.odyssey.recommendation.v2.BeamSelectorTest.START;
import static com.odyssey.recommendation.v2.BeamSelectorTest.ids;
import static com.odyssey.recommendation.v2.BeamSelectorTest.request;
import static com.odyssey.recommendation.v2.BeamSelectorTest.store;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.CourseSelector.Selection;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.store.entity.StoreKind;

/** 자리마다 확정하는 선택기가 영업 확인 결과와 제외 매장을 다루는 방식을 확인한다. */
class BandShuffleSelectorTest {

	private static final CandidatePool LUNCH = new CandidatePool(CONTEXT, Map.of(TimeSlot.LUNCH, List.of(
		store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.KOREAN, 0, 80))));
	private static final CandidatePool PLAY = new CandidatePool(CONTEXT, Map.of(TimeSlot.AFTERNOON, List.of(
		store(1, StoreKind.PLAY, 0, 99))));

	private static BandShuffleSelector selector(Result result) {
		return new BandShuffleSelector((id, weekday, time, stay) -> result);
	}

	@Test
	@DisplayName("1위가 닫혀 있음 → 다음 점수대의 영업 중인 매장을 고르고 목록 순번과 점수대 크기를 담는다")
	void choosesNextBandWhenTopIsClosed() {
		// given
		BandShuffleSelector selector = new BandShuffleSelector(
			(id, weekday, time, stay) -> id.equals("p1") ? Result.NOT_OPEN : Result.OPEN);

		// when
		Selection selected = selector.select(request(SLOTS.subList(0, 1), 7), LUNCH);

		// then
		assertThat(ids(selected)).containsExactly(2L);
		assertThat(selected.picks().getFirst().stop().candidateRank()).isEqualTo(2);
		assertThat(selected.picks().getFirst().bandSize()).isEqualTo(1);
	}

	@Test
	@DisplayName("필수 자리의 외부 조회가 실패함 → 다음 후보로 넘어가지 않고 VERIFICATION_UNAVAILABLE 로 멈춘다")
	void stopsWhenLookupFails() {
		// when
		Selection selected = selector(Result.UNAVAILABLE).select(request(SLOTS.subList(0, 1), 7), LUNCH);

		// then
		assertThat(selected.picks()).isEmpty();
		assertThat(selected.interruption())
			.isEqualTo(new Interruption(TimeSlot.LUNCH, "VERIFICATION_UNAVAILABLE"));
	}

	@Test
	@DisplayName("영업시간이 없는 놀거리 → hoursUnknown 으로 받는다")
	void acceptsUnknownHoursForPlay() {
		// when
		Selection selected = selector(Result.HOURS_UNKNOWN).select(request(List.of(SLOTS.get(2)), 7), PLAY);

		// then
		assertThat(ids(selected)).containsExactly(1L);
		assertThat(selected.picks().getFirst().stop().hoursUnknown()).isTrue();
	}

	@Test
	@DisplayName("영업시간이 없는 한식당 → 받지 않고 NO_VERIFIED_MATCH 로 멈춘다")
	void rejectsUnknownHoursForRestaurant() {
		// when
		Selection selected = selector(Result.HOURS_UNKNOWN).select(request(SLOTS.subList(0, 1), 7), LUNCH);

		// then
		assertThat(selected.picks()).isEmpty();
		assertThat(selected.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("선택 자리의 후보를 모두 제외함 → 멈추지 않고 ALL_EXCLUDED 로 건너뛴다")
	void skipsOptionalSlotWhenEveryCandidateIsExcluded() {
		// given
		Request request = new Request(List.of(SLOTS.get(2)), START, Set.of(1L), Weekday.FRI, 7,
			WalkPreference.DEFAULT);

		// when
		Selection selected = selector(Result.OPEN).select(request, PLAY);

		// then
		assertThat(selected.picks()).isEmpty();
		assertThat(selected.interruption()).isNull();
		assertThat(selected.skipped()).containsExactly(new Skipped(TimeSlot.AFTERNOON, 3, "ALL_EXCLUDED"));
	}

	@Test
	@DisplayName("선택 자리 근처에 후보가 없음 → 멈추지 않고 NO_NEARBY_MATCH 로 건너뛴다")
	void skipsOptionalSlotWithoutNearbyCandidates() {
		// when
		Selection selected = selector(Result.OPEN).select(request(List.of(SLOTS.get(2)), 7),
			new CandidatePool(CONTEXT, Map.of()));

		// then
		assertThat(selected.skipped()).containsExactly(new Skipped(TimeSlot.AFTERNOON, 3, "NO_NEARBY_MATCH"));
	}

	@Test
	@DisplayName("점심에서만 1번 매장을 뺌 → 점심은 2번, 저녁은 1번을 고른다")
	void slotExclusionOnlyAppliesToThatSlot() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.KOREAN, 200, 98)),
			TimeSlot.DINNER, List.of(store(1, StoreKind.KOREAN, 0, 98), store(2, StoreKind.KOREAN, 200, 99))));
		Request request = new Request(List.of(SLOTS.get(0), SLOTS.get(3)), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Map.of(1, Set.of(1L)));

		// when
		Selection selected = selector(Result.OPEN).select(request, pool);

		// then
		assertThat(ids(selected)).containsExactly(2L, 1L);
	}
}
