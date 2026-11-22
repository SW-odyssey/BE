package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/** {@link CandidatePool} 이 미리 읽은 후보를 출발점·종류로 거르는지 확인한다. */
class CandidatePoolTest {

	private static final V2Context CONTEXT = new V2Context("nov", "2025-11", Weekday.FRI, TimeSlot.AFTERNOON);
	/** 서로 약 700m 떨어진 두 출발점 */
	private static final GeoPoint WEST = new GeoPoint(37.5660, 126.9900);
	private static final GeoPoint EAST = new GeoPoint(37.5660, 126.9980);

	private static Candidate candidate(long id, StoreKind kind, double lat, double lng, double percentile) {
		return new Candidate(new Place(id, "매장" + id, id, kind, lat, lng, null, "place-" + id), "m" + id,
			percentile);
	}

	private static List<Long> ids(List<Selected> selected) {
		return selected.stream().map(s -> s.candidate().place().id()).toList();
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({"CAFE, 1", "SHOP, 2"})
	@DisplayName("같은 시간대에 카페와 쇼핑이 섞여 있음 → 요청한 종류만 준다")
	void filtersByKind(StoreKind kind, long expectedId) {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.AFTERNOON, List.of(
			candidate(1, StoreKind.CAFE, 37.5661, 126.9901, 99),
			candidate(2, StoreKind.SHOP, 37.5661, 126.9902, 98))));

		// when
		List<Selected> near = pool.near(TimeSlot.AFTERNOON, kind, WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(ids(near)).containsExactly(expectedId);
	}

	@ParameterizedTest(name = "출발점 {0} → {1}")
	@CsvSource({"WEST, 1", "EAST, 2"})
	@DisplayName("출발점이 바뀜 → 그 좌표에서 300m 안의 후보만 준다")
	void filtersByDistanceFromOrigin(String originName, long expectedId) {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.DINNER, List.of(
			candidate(1, StoreKind.KOREAN, 37.5665, 126.9910, 99),
			candidate(2, StoreKind.KOREAN, 37.5665, 126.9975, 99))));
		GeoPoint origin = originName.equals("WEST") ? WEST : EAST;

		// when
		List<Selected> near = pool.near(TimeSlot.DINNER, StoreKind.KOREAN, origin, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(ids(near)).containsExactly(expectedId);
	}

	@ParameterizedTest
	@CsvSource({"MON, true", "TUE, true", "WED, true", "THU, true", "FRI, true", "SAT, false", "SUN, false"})
	@DisplayName("방문 백분위 65인 오후 놀거리 → 월요일부터 금요일까지만 후보에 든다")
	void afternoonPlayMinimum(Weekday weekday, boolean eligible) {
		// given
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(weekday);
		Candidate play = candidate(1, StoreKind.PLAY, WEST.lat(), WEST.lng(), 65);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), List.of(play)));

		// when
		List<Selected> selected = pool.near(slot.slot(), StoreKind.PLAY, WEST, slot.minPercentile());

		// then
		assertThat(selected).hasSize(eligible ? 1 : 0);
	}

	@Test
	@DisplayName("여러 후보가 반경 안에 있음 → 목록 API 와 같이 방문 백분위 내림차순이다")
	void keepsListOrder() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.DINNER, List.of(
			candidate(1, StoreKind.KOREAN, 37.5661, 126.9901, 90),
			candidate(2, StoreKind.KOREAN, 37.5662, 126.9902, 99),
			candidate(3, StoreKind.KOREAN, 37.5663, 126.9903, 95))));

		// when
		List<Selected> near = pool.near(TimeSlot.DINNER, StoreKind.KOREAN, WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(ids(near)).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("읽지 않은 시간대 → 빈 목록이다")
	void unreadSlot() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of());

		// when
		List<Selected> near = pool.near(TimeSlot.LATE_NIGHT, StoreKind.BAR, WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(near).isEmpty();
	}
}
