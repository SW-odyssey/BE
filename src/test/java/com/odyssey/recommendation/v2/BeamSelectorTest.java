package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

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
}
