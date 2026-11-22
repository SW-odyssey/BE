package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.CourseSelector.Selection;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

class BeamSelectorTest {
	static final GeoPoint START = new GeoPoint(37.56, 126.99);
	static final V2Context CONTEXT = new V2Context("nov", "2025-11", Weekday.FRI, TimeSlot.LUNCH);
	static final List<CourseSlot> SLOTS = CourseSchedule.slots(Weekday.FRI, StoreKind.CAFE, TimeSlot.LUNCH, false);

	static Candidate store(long id, StoreKind kind, int northMeters, double percentile) {
		double lat = START.lat() + northMeters / 111195.0;
		return new Candidate(new Place(id, "매장" + id, id, kind, lat, START.lng(), null, "p" + id),
			"m" + id, percentile);
	}

	static Request request(List<CourseSlot> slots, long seed) {
		return new Request(slots, START, Set.of(), Weekday.FRI, seed, WalkPreference.DEFAULT);
	}

	@Test
	@DisplayName("짧게 걷기를 고르면 앞 매장에서 150m 안의 카페도 선택한다")
	void shortWalkAcceptsNearbyCafe() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 50, 99))));
		List<CourseSlot> slots = SLOTS.subList(0, 2);
		Request shortWalk = new Request(slots, START, Set.of(), Weekday.FRI, 7, WalkPreference.SHORT);
		BeamSelector selector = new BeamSelector();

		assertThat(ids(selector.select(request(slots, 7), pool))).containsExactly(1L);
		assertThat(ids(selector.select(shortWalk, pool))).containsExactly(1L, 2L);
	}

	static List<Long> ids(Selection selection) {
		return selection.picks().stream().map(pick -> pick.stop().place().id()).toList();
	}

	@Test
	@DisplayName("구간별 확정은 점심 매장 근처로 돌아오지만 빔은 다른 저녁 매장을 고른다")
	void avoidsReturningToLunch() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99)),
			TimeSlot.DINNER, List.of(store(3, StoreKind.KOREAN, 20, 99), store(4, StoreKind.KOREAN, 400, 90))));
		Request request = request(List.of(SLOTS.get(0), SLOTS.get(1), SLOTS.get(3)), 7);
		CourseSelector greedy = new BandShuffleSelector((id, weekday, time, stay) -> Result.OPEN);

		assertThat(ids(greedy.select(request, pool))).containsExactly(1L, 2L, 3L);
		assertThat(ids(new BeamSelector().select(request, pool))).containsExactly(1L, 2L, 4L);
	}

	@Test
	@DisplayName("선택 자리에 후보가 있어도 필수 저녁을 채울 수 있는 건너뛰기 경로를 남긴다")
	void skipsReachableOptionalStop() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99), store(3, StoreKind.PLAY, 630, 99)),
			TimeSlot.DINNER, List.of(store(4, StoreKind.KOREAN, -220, 99))));

		Selection selected = new BeamSelector().select(request(SLOTS.subList(0, 4), 7), pool);

		assertThat(ids(selected)).containsExactly(1L, 2L, 4L);
		assertThat(selected.interruption()).isNull();
		assertThat(selected.skipped()).singleElement().satisfies(skip -> {
			assertThat(skip.position()).isEqualTo(3);
			assertThat(skip.reason()).isEqualTo("SKIPPED_FOR_ROUTE");
		});
	}

	@Test
	@DisplayName("같은 seed는 같은 코스를 고르고 점수·거리 범위 밖 후보는 seed가 달라도 뽑히지 않는다")
	void choosesReproduciblyWithinBothMargins() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.LUNCH, List.of(
			store(1, StoreKind.KOREAN, 5, 99), store(2, StoreKind.KOREAN, 50, 97),
			store(3, StoreKind.KOREAN, 200, 99), store(4, StoreKind.KOREAN, 0, 95))));
		BeamSelector beam = new BeamSelector();
		Set<Long> chosen = new java.util.HashSet<>();
		for (long seed : List.of(0L, 1L, 7L, 4096L)) {
			Request request = request(SLOTS.subList(0, 1), seed);
			Selection first = beam.select(request, pool);
			assertThat(beam.select(request, pool)).isEqualTo(first);
			chosen.addAll(ids(first));
		}
		assertThat(chosen).containsExactlyInAnyOrder(1L, 2L);
	}

	private static CandidatePool lunchAndDinnerAtBothPlaces() {
		return new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.KOREAN, 200, 98)),
			TimeSlot.DINNER, List.of(store(1, StoreKind.KOREAN, 0, 98), store(2, StoreKind.KOREAN, 200, 99))));
	}

	private static final List<CourseSlot> LUNCH_AND_DINNER = List.of(SLOTS.get(0), SLOTS.get(3));

	@Test
	@DisplayName("선택 자리 후보가 빔 너비(300)보다 많음 → 건너뛰는 경로도 남겨 뒤의 필수 자리를 채운다")
	void keepsSkipPathWhenOptionalSlotHasMoreCandidatesThanBeamWidth() {
		// given: 카페 11곳 x 놀거리 30곳 = 330 경로. 놀거리를 고르면 저녁집(-220m)이 반경 500m 밖이다.
		List<Candidate> afternoon = new ArrayList<>();
		for (int id = 2; id <= 12; id++) {
			afternoon.add(store(id, StoreKind.CAFE, 200, 99));
		}
		for (int id = 100; id < 130; id++) {
			afternoon.add(store(id, StoreKind.PLAY, 630, 99));
		}
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, afternoon,
			TimeSlot.DINNER, List.of(store(4000, StoreKind.KOREAN, -220, 99))));

		// when
		Selection selected = new BeamSelector().select(request(SLOTS.subList(0, 4), 7), pool);

		// then
		assertThat(selected.interruption()).isNull();
		assertThat(ids(selected)).hasSize(3).startsWith(1L).endsWith(4000L);
		assertThat(selected.skipped()).extracting(Skipped::position).containsExactly(3);
	}

	@Test
	@DisplayName("제외 매장이 없음 → 점수 합이 높고 가까운 점심 1·저녁 2 를 고른다")
	void picksNearestPairWithoutExclusions() {
		// when
		Selection selected = new BeamSelector().select(request(LUNCH_AND_DINNER, 7), lunchAndDinnerAtBothPlaces());

		// then
		assertThat(ids(selected)).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("저녁(4번)에서만 2번 매장을 뺌 → 점심에 2번을 쓰고 저녁에 1번을 쓴다")
	void slotExclusionOnlyAppliesToThatSlot() {
		// given
		Request request = new Request(LUNCH_AND_DINNER, START, Set.of(), Weekday.FRI, 7, WalkPreference.DEFAULT,
			Map.of(4, Set.of(2L)));

		// when
		Selection selected = new BeamSelector().select(request, lunchAndDinnerAtBothPlaces());

		// then
		assertThat(ids(selected)).containsExactly(2L, 1L);
		assertThat(selected.interruption()).isNull();
	}

	@Test
	@DisplayName("모든 자리에서 2번 매장을 뺌 → 점심 1번만 고르고 저녁에서 멈춘다")
	void globalExclusionAppliesToEverySlot() {
		// given
		Request request = new Request(LUNCH_AND_DINNER, START, Set.of(2L), Weekday.FRI, 7, WalkPreference.DEFAULT);

		// when
		Selection selected = new BeamSelector().select(request, lunchAndDinnerAtBothPlaces());

		// then
		assertThat(ids(selected)).containsExactly(1L);
		assertThat(selected.interruption()).isEqualTo(new Interruption(TimeSlot.DINNER, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("첫 필수 자리에 후보가 없음 → 정류장 없이 그 자리에서 NO_VERIFIED_MATCH 로 멈춘다")
	void stopsAtFirstRequiredSlotWithoutCandidates() {
		// when
		Selection selected = new BeamSelector().select(request(SLOTS.subList(0, 1), 7),
			new CandidatePool(CONTEXT, Map.of()));

		// then
		assertThat(selected.picks()).isEmpty();
		assertThat(selected.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("선택 자리에 후보가 하나도 없음 → 중단하지 않고 NO_VERIFIED_MATCH 로 건너뛴다")
	void skipsOptionalSlotWithoutCandidates() {
		// when
		Selection selected = new BeamSelector().select(request(List.of(SLOTS.get(2)), 7),
			new CandidatePool(CONTEXT, Map.of()));

		// then
		assertThat(selected.picks()).isEmpty();
		assertThat(selected.interruption()).isNull();
		assertThat(selected.skipped()).containsExactly(new Skipped(TimeSlot.AFTERNOON, 3, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("앞 매장에서 450m 떨어진 카페 → 기본 걷기는 500m 까지 받고 짧게 걷기는 300m 까지만 받는다")
	void walkRadiusBetweenStops() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 450, 99))));
		List<CourseSlot> slots = SLOTS.subList(0, 2);
		Request shortWalk = new Request(slots, START, Set.of(), Weekday.FRI, 7, WalkPreference.SHORT);

		// when
		Selection byDefault = new BeamSelector().select(request(slots, 7), pool);
		Selection byShort = new BeamSelector().select(shortWalk, pool);

		// then
		assertThat(ids(byDefault)).containsExactly(1L, 2L);
		assertThat(ids(byShort)).containsExactly(1L);
		assertThat(byShort.interruption()).isEqualTo(new Interruption(TimeSlot.AFTERNOON, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("점심 매장에서 50m 안의 저녁 후보 → 기본 걷기는 이전 방문지 100m 안이라 빼고 짧게 걷기는 받는다")
	void defaultWalkAvoidsReturningNearEarlierStops() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 290, 99)),
			TimeSlot.DINNER, List.of(store(3, StoreKind.KOREAN, 50, 99))));
		List<CourseSlot> slots = List.of(SLOTS.get(0), SLOTS.get(1), SLOTS.get(3));
		Request shortWalk = new Request(slots, START, Set.of(), Weekday.FRI, 7, WalkPreference.SHORT);

		// when
		Selection byDefault = new BeamSelector().select(request(slots, 7), pool);
		Selection byShort = new BeamSelector().select(shortWalk, pool);

		// then
		assertThat(ids(byDefault)).containsExactly(1L, 2L);
		assertThat(byDefault.interruption()).isEqualTo(new Interruption(TimeSlot.DINNER, "NO_VERIFIED_MATCH"));
		assertThat(ids(byShort)).containsExactly(1L, 2L, 3L);
	}
}
