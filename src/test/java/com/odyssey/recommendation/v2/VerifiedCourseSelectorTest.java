package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.BeamSelectorTest.CONTEXT;
import static com.odyssey.recommendation.v2.BeamSelectorTest.SLOTS;
import static com.odyssey.recommendation.v2.BeamSelectorTest.START;
import static com.odyssey.recommendation.v2.BeamSelectorTest.ids;
import static com.odyssey.recommendation.v2.BeamSelectorTest.request;
import static com.odyssey.recommendation.v2.BeamSelectorTest.store;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.CourseHoursLookup.Hours;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.CourseSelector.Selection;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

class VerifiedCourseSelectorTest {

	@Test
	@DisplayName("두 번째 매장이 닫히면 빔을 다시 돌리고 첫 매장은 요청마다 한 번만 조회한다")
	void replansAndCachesOnlyWithinRequest() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99), store(3, StoreKind.CAFE, 220, 80))));
		List<String> calls = new ArrayList<>();
		CourseHoursLookup lookup = id -> {
			calls.add(id);
			return Hours.fixed(id.equals("p2") ? Result.NOT_OPEN : Result.OPEN);
		};
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), lookup);
		Request request = request(SLOTS.subList(0, 2), 7);

		Selection result = selector.select(request, pool);

		assertThat(ids(result)).containsExactly(1L, 3L);
		assertThat(result.picks().getLast().stop().candidateRank()).isEqualTo(2);
		assertThat(calls).containsExactly("p1", "p2", "p3");
		assertThat(selector.select(request, pool)).isEqualTo(result);
		assertThat(calls).containsExactly("p1", "p2", "p3", "p1", "p2", "p3");
	}

	@Test
	@DisplayName("새 매장이 닫혀도 재탐색마다 새 매장을 우선하고, 모두 닫혔으면 직전 매장을 선택한다")
	void retainsRegenerationPriorityAfterClosedStops() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99),
				store(3, StoreKind.CAFE, 220, 99), store(4, StoreKind.CAFE, 240, 99))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(Set.of("p3", "p4").contains(id) ? Result.NOT_OPEN : Result.OPEN);
		});
		Request request = new Request(SLOTS.subList(0, 2), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Set.of(1L, 2L), Map.of());

		Selection result = selector.select(request, pool);

		assertThat(ids(result)).containsExactly(1L, 2L);
		assertThat(calls.subList(1, 3)).containsExactlyInAnyOrder("p3", "p4");
		assertThat(calls.getLast()).isEqualTo("p2");
	}

	@Test
	@DisplayName("전체 교체는 기존 제외 조건을 함께 적용해 같은 방문 수를 모두 새 매장으로 채운다")
	void replacesEveryStopWhenACompleteRouteExists() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(3, StoreKind.KOREAN, 20, 90),
				store(5, StoreKind.KOREAN, 10, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99), store(4, StoreKind.CAFE, 240, 90))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(Result.OPEN);
		});
		Request rebuild = new Request(SLOTS.subList(0, 2), START, Set.of(5L), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Set.of(1L, 2L), Map.of());

		Selection result = selector.select(rebuild, pool);

		assertThat(result.interruption()).isNull();
		assertThat(ids(result)).containsExactly(3L, 4L);
		assertThat(calls).containsExactly("p1", "p2", "p3", "p4");
	}

	@Test
	@DisplayName("요청 자리를 모두 채워도 직전 방문 수보다 적으면 부분 교체로 돌아간다")
	void fallsBackWhenAllNewRouteHasFewerStopsThanPreviousCourse() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(3, StoreKind.KOREAN, 20, 90)),
			TimeSlot.AFTERNOON, List.of(store(4, StoreKind.CAFE, 200, 99))));
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> Hours.fixed(Result.OPEN));
		Request rebuild = new Request(SLOTS.subList(0, 2), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Set.of(1L, 2L, 5L), Map.of());

		// when
		Selection result = selector.select(rebuild, pool);

		// then
		assertThat(result.interruption()).isNull();
		assertThat(ids(result)).containsExactly(1L, 4L);
	}

	@Test
	@DisplayName("새 카페가 닫아서 방문 수가 줄면 부분 교체로 돌아가고 조회 결과를 재사용한다")
	void fallsBackWithSharedHoursWhenTheNewRouteCannotBeVerified() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(3, StoreKind.KOREAN, 20, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99), store(4, StoreKind.CAFE, 240, 99))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(id.equals("p4") ? Result.NOT_OPEN : Result.OPEN);
		});
		Request rebuild = new Request(SLOTS.subList(0, 2), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Set.of(1L, 2L), Map.of());

		Selection result = selector.select(rebuild, pool);

		assertThat(result.interruption()).isNull();
		assertThat(ids(result)).containsExactly(3L, 2L);
		assertThat(calls).containsExactly("p3", "p4", "p2");
	}

	@Test
	@DisplayName("전체 교체가 조회 상한에 닿아도 확인해 둔 부분 교체를 끝까지 반환한다")
	void preservesVerifiedFallbackWhenAllNewCandidatesConsumeTheLookupLimit() {
		List<Candidate> lunches = new ArrayList<>();
		lunches.add(store(1, StoreKind.KOREAN, 0, 99));
		lunches.add(store(3, StoreKind.KOREAN, -220, 99));
		for (int id = 100; id <= 140; id++) {
			lunches.add(store(id, StoreKind.KOREAN, id - 100, 90));
		}
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, lunches,
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, -430, 99),
				store(500, StoreKind.CAFE, 310, 90))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(Set.of("p1", "p2", "p500").contains(id) ? Result.OPEN : Result.NOT_OPEN);
		});
		Request rebuild = new Request(SLOTS.subList(0, 2), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Set.of(1L, 2L), Map.of());

		Selection result = selector.select(rebuild, pool);

		assertThat(result.interruption()).isNull();
		assertThat(ids(result)).containsExactly(1L, 2L);
		assertThat(calls).hasSize(40).doesNotHaveDuplicates();
		assertThat(calls.subList(0, 3)).containsExactly("p3", "p1", "p2");
	}

	@ParameterizedTest
	@CsvSource({"40, NO_VERIFIED_MATCH", "41, CHECK_LIMIT"})
	@DisplayName("40곳을 확인한 뒤 후보가 없을 때와 한 곳을 더 확인해야 할 때를 구분한다")
	void limitsLookups(int count, String expectedReason) {
		List<Candidate> candidates = new ArrayList<>();
		for (int id = 1; id <= count; id++) {
			candidates.add(store(id, StoreKind.KOREAN, id, 99));
		}
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.LUNCH, candidates));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(Result.NOT_OPEN);
		});

		Selection result = selector.select(request(SLOTS.subList(0, 1), 7), pool);

		assertThat(result.interruption().reason()).isEqualTo(expectedReason);
		assertThat(result.picks()).isEmpty();
		assertThat(calls).hasSize(40).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("선택 자리에서 외부 조회가 실패하면 그 자리를 건너뛰고 직전 매장부터 다시 이어간다")
	void skipsUnavailableOptionalAndReplans() {
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.AFTERNOON, List.of(store(1, StoreKind.CAFE, 0, 99), store(2, StoreKind.PLAY, 200, 99)),
			TimeSlot.DINNER, List.of(store(3, StoreKind.KOREAN, 400, 99))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new CourseSelectionConfig().courseSelector(id -> {
			calls.add(id);
			return Hours.fixed(id.equals("p2") ? Result.UNAVAILABLE : Result.OPEN);
		});

		Selection result = selector.select(request(SLOTS.subList(1, 4), 7), pool);

		assertThat(ids(result)).containsExactly(1L, 3L);
		assertThat(result.interruption()).isNull();
		assertThat(result.skipped()).singleElement().satisfies(skip -> {
			assertThat(skip.position()).isEqualTo(3);
			assertThat(skip.reason()).isEqualTo("VERIFICATION_UNAVAILABLE");
		});
		assertThat(calls).containsExactly("p1", "p2", "p3");
	}

	@Test
	@DisplayName("같은 Google 장소는 한 번 조회해도 점심과 저녁의 방문 판정은 따로 한다")
	void evaluatesCachedHoursAtActualArrival() {
		Candidate lunch = store(1, StoreKind.KOREAN, 0, 99);
		Candidate dinner = store(2, StoreKind.KOREAN, 200, 99);
		Place original = dinner.place();
		Place alias = new Place(original.id(), original.name(),
			original.cellId(), original.kind(), original.lat(), original.lng(), null, lunch.place().googlePlaceId());
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.LUNCH, List.of(lunch),
			TimeSlot.DINNER, List.of(new Candidate(alias, dinner.mgmtNo(), dinner.percentile()))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return (weekday, arrival, minutes) -> arrival.getHour() == 12 ? Result.OPEN : Result.NOT_OPEN;
		});

		Selection result = selector.select(request(List.of(SLOTS.get(0), SLOTS.get(3)), 7), pool);

		assertThat(ids(result)).containsExactly(1L);
		assertThat(result.interruption().reason()).isEqualTo("NO_VERIFIED_MATCH");
		assertThat(calls).containsExactly("p1");
	}

	@Test
	@DisplayName("점심엔 열고 저녁엔 닫는 매장 → 닫힌 저녁 자리에서만 빼고 그 매장을 점심에 다시 쓴다")
	void closedPlaceIsExcludedOnlyAtTheClosedSlot() {
		// given: 1번은 늘 열고 2번은 저녁(17시 이후)에 닫는다. 처음 계획은 점심 1·저녁 2 다.
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.KOREAN, 200, 98)),
			TimeSlot.DINNER, List.of(store(1, StoreKind.KOREAN, 0, 98), store(2, StoreKind.KOREAN, 200, 99))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return (weekday, arrival, minutes) -> id.equals("p2") && arrival.getHour() >= 17
				? Result.NOT_OPEN : Result.OPEN;
		});

		// when
		Selection result = selector.select(request(List.of(SLOTS.get(0), SLOTS.get(3)), 7), pool);

		// then
		assertThat(ids(result)).containsExactly(2L, 1L);
		assertThat(result.interruption()).isNull();
		assertThat(calls).containsExactly("p1", "p2");
	}

	@Test
	@DisplayName("Google 장소 ID 가 없는 매장 → 조회하지 않고 모든 자리에서 빼 다른 후보를 고른다")
	void placeWithoutGoogleIdIsExcludedEverywhere() {
		// given
		Place original = store(1, StoreKind.KOREAN, 0, 99).place();
		Candidate withoutId = new Candidate(new Place(original.id(), original.name(), original.cellId(),
			original.kind(), original.lat(), original.lng(), null, null), "m1", 99);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(withoutId, store(2, StoreKind.KOREAN, 0, 98))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(Result.OPEN);
		});

		// when
		Selection result = selector.select(request(SLOTS.subList(0, 1), 7), pool);

		// then
		assertThat(ids(result)).containsExactly(2L);
		assertThat(calls).containsExactly("p2");
	}

	@Test
	@DisplayName("필수 자리에서 외부 조회가 실패함 → 앞 정류장은 남기고 VERIFICATION_UNAVAILABLE 로 멈춘다")
	void stopsAtRequiredSlotWhenLookupFails() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99)),
			TimeSlot.AFTERNOON, List.of(store(2, StoreKind.CAFE, 200, 99))));
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(),
			id -> Hours.fixed(id.equals("p2") ? Result.UNAVAILABLE : Result.OPEN));

		// when
		Selection result = selector.select(request(SLOTS.subList(0, 2), 7), pool);

		// then
		assertThat(ids(result)).containsExactly(1L);
		assertThat(result.interruption()).isEqualTo(new Interruption(TimeSlot.AFTERNOON, "VERIFICATION_UNAVAILABLE"));
	}

	@Test
	@DisplayName("영업시간이 없는 놀거리 → hoursUnknown 으로 받고, 영업시간이 없는 한식당은 받지 않는다")
	void acceptsUnknownHoursOnlyForPlayAndShop() {
		// given
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> Hours.fixed(Result.HOURS_UNKNOWN));
		CandidatePool play = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.AFTERNOON, List.of(store(1, StoreKind.PLAY, 0, 99))));
		CandidatePool restaurant = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99))));

		// when
		Selection playResult = selector.select(request(List.of(SLOTS.get(2)), 7), play);
		Selection restaurantResult = selector.select(request(SLOTS.subList(0, 1), 7), restaurant);

		// then
		assertThat(playResult.picks()).singleElement().satisfies(pick -> {
			assertThat(pick.stop().hoursUnknown()).isTrue();
		});
		assertThat(restaurantResult.picks()).isEmpty();
		assertThat(restaurantResult.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("5번 선택 자리의 조회가 실패함 → 4번 저녁은 남기고 5번을 VERIFICATION_UNAVAILABLE 로 건너뛴다")
	void skipsUnavailableDinnerPlay() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.DINNER, List.of(
			store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.PLAY, 200, 99))));
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(),
			id -> Hours.fixed(id.equals("p2") ? Result.UNAVAILABLE : Result.OPEN));

		// when
		Selection result = selector.select(request(SLOTS.subList(3, 5), 7), pool);

		// then
		assertThat(ids(result)).containsExactly(1L);
		assertThat(result.skipped()).containsExactly(new Skipped(TimeSlot.DINNER, 5, "VERIFICATION_UNAVAILABLE"));
		assertThat(result.interruption()).isNull();
	}

	@Test
	@DisplayName("일정이 비어 있는데 계획이 중단을 담아 옴 → 자리 번호를 찾다 실패하지 않고 그대로 돌려준다")
	void keepsPlannerInterruptionForEmptySchedule() {
		// given
		Interruption interruption = new Interruption(TimeSlot.LUNCH, "NO_VERIFIED_MATCH");
		CourseSelector planner = (request, pool) -> new Selection(List.of(), List.of(), interruption);
		CourseSelector selector = new VerifiedCourseSelector(planner, id -> Hours.fixed(Result.OPEN));

		// when
		Selection result = selector.select(request(List.of(), 7), new CandidatePool(CONTEXT, Map.of()));

		// then
		assertThat(result.picks()).isEmpty();
		assertThat(result.interruption()).isEqualTo(interruption);
	}

	@Test
	@DisplayName("요청의 자리별 제외 → 처음 계획부터 적용하고, 조회 중 닫힌 매장 제외와 함께 다시 짤 때도 유지한다")
	void keepsRequestSlotExclusionsAcrossReplans() {
		// given: 점심 1번은 요청이 점심에서 빼고, 저녁 3번은 조회해 보니 닫혀 있다
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(
			TimeSlot.LUNCH, List.of(store(1, StoreKind.KOREAN, 0, 99), store(2, StoreKind.KOREAN, 100, 90)),
			TimeSlot.DINNER, List.of(store(3, StoreKind.KOREAN, 400, 99), store(4, StoreKind.KOREAN, 450, 90))));
		List<String> calls = new ArrayList<>();
		CourseSelector selector = new VerifiedCourseSelector(new BeamSelector(), id -> {
			calls.add(id);
			return Hours.fixed(id.equals("p3") ? Result.NOT_OPEN : Result.OPEN);
		});
		Request request = new Request(List.of(SLOTS.get(0), SLOTS.get(3)), START, Set.of(), Weekday.FRI, 7,
			WalkPreference.DEFAULT, Map.of(1, Set.of(1L)));

		// when
		Selection result = selector.select(request, pool);

		// then
		assertThat(ids(result)).containsExactly(2L, 4L);
		assertThat(calls).doesNotContain("p1");
	}
}
