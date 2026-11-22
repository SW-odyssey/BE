package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.BeamSelectorTest.CONTEXT;
import static com.odyssey.recommendation.v2.BeamSelectorTest.SLOTS;
import static com.odyssey.recommendation.v2.BeamSelectorTest.ids;
import static com.odyssey.recommendation.v2.BeamSelectorTest.request;
import static com.odyssey.recommendation.v2.BeamSelectorTest.store;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseHoursLookup.Hours;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.CourseSelector.Selection;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
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
}
