package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
		List<Selected> near = pool.near(TimeSlot.AFTERNOON, Set.of(kind), WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

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
		List<Selected> near = pool.near(TimeSlot.DINNER, Set.of(StoreKind.KOREAN), origin, CourseSchedule.DEFAULT_MIN_PERCENTILE);

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
		List<Selected> selected = pool.near(slot.slot(), Set.of(StoreKind.PLAY), WEST, slot.minPercentile());

		// then
		assertThat(selected).hasSize(eligible ? 1 : 0);
	}

	@ParameterizedTest(name = "{0} 방문 백분위 {1} → 후보 {2}")
	@CsvSource({"FRI, 59.99, false", "FRI, 60, true", "SAT, 74.99, false", "SAT, 75, true"})
	@DisplayName("오후 놀거리 하한 경계 → 하한 미만은 빼고 하한과 같은 값은 넣는다")
	void afternoonPlayMinimumBoundary(Weekday weekday, double percentile, boolean eligible) {
		// given
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(weekday);
		Candidate play = candidate(1, StoreKind.PLAY, WEST.lat(), WEST.lng(), percentile);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), List.of(play)));

		// when
		List<Selected> selected = pool.near(slot.slot(), Set.of(StoreKind.PLAY), WEST, slot.minPercentile());

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
		List<Selected> near = pool.near(TimeSlot.DINNER, Set.of(StoreKind.KOREAN), WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(ids(near)).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("놀거리·쇼핑 후보가 섞임 → 종류를 합쳐 점수가 높고 거리가 가까운 순으로 고른다")
	void ranksAcrossKinds() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, StoreKind.PLAY, 37.5663, WEST.lng(), 90),
			candidate(2, StoreKind.SHOP, 37.5661, WEST.lng(), 90),
			candidate(3, StoreKind.PLAY, 37.5664, WEST.lng(), 95),
			candidate(4, StoreKind.CAFE, WEST.lat(), WEST.lng(), 99));
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(TimeSlot.AFTERNOON, candidates));

		// when
		List<Selected> selected = pool.near(TimeSlot.AFTERNOON, Set.of(StoreKind.PLAY, StoreKind.SHOP), WEST, 75);

		// then
		assertThat(ids(selected)).containsExactly(3L, 2L, 1L);
	}

	@Test
	@DisplayName("구간 거리의 하한·상한과 이전 방문지 반경을 각각 적용한다")
	void walkingDistances() {
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(Weekday.FRI);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), List.of(
			candidate(1, StoreKind.PLAY, WEST.lat() + 0.001, WEST.lng(), 99),
			candidate(2, StoreKind.PLAY, WEST.lat() + 0.002, WEST.lng(), 98),
			candidate(3, StoreKind.PLAY, WEST.lat() + 0.004, WEST.lng(), 97),
			candidate(4, StoreKind.PLAY, WEST.lat() + 0.005, WEST.lng(), 96))));
		CandidatePool.LegCandidates leg = pool.forLeg(slot, 150, 500, 100);

		assertThat(leg.near(WEST, List.of(), Set.of()))
			.extracting(row -> row.selected().candidate().place().id()).containsExactly(2L, 3L);
		GeoPoint earlier = new GeoPoint(WEST.lat() + 0.0022, WEST.lng());
		assertThat(leg.near(WEST, List.of(earlier), Set.of()))
			.extracting(row -> row.selected().candidate().place().id()).containsExactly(3L);
		assertThat(leg.near(WEST, List.of(earlier), Set.of(3L))).isEmpty();
	}

	@Test
	@DisplayName("읽지 않은 시간대 → 빈 목록이다")
	void unreadSlot() {
		// given
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of());

		// when
		List<Selected> near = pool.near(TimeSlot.LATE_NIGHT, Set.of(StoreKind.BAR), WEST, CourseSchedule.DEFAULT_MIN_PERCENTILE);

		// then
		assertThat(near).isEmpty();
	}

	@Test
	@DisplayName("제외한 매장 → 후보에서 빠지지만 뒤 후보의 순번(rank)은 목록 API 기준 그대로다")
	void excludedCandidateKeepsLaterRanks() {
		// given
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(Weekday.FRI);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), List.of(
			candidate(1, StoreKind.PLAY, WEST.lat() + 0.0005, WEST.lng(), 99),
			candidate(2, StoreKind.PLAY, WEST.lat() + 0.0006, WEST.lng(), 98),
			candidate(3, StoreKind.PLAY, WEST.lat() + 0.0007, WEST.lng(), 97))));
		CandidatePool.LegCandidates leg = pool.forLeg(slot, 0, 500, 0);

		// when
		List<CandidatePool.LegCandidates.Ranked> nearby = leg.near(WEST, List.of(), Set.of(2L));

		// then
		assertThat(nearby).extracting(row -> row.selected().candidate().place().id()).containsExactly(1L, 3L);
		assertThat(nearby).extracting(CandidatePool.LegCandidates.Ranked::rank).containsExactly(1, 3);
	}

	@Test
	@DisplayName("이전 방문지 100m 안의 후보 → 순번도 차지하지 않고 빠진다")
	void avoidedCandidateDoesNotTakeARank() {
		// given
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(Weekday.FRI);
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), List.of(
			candidate(1, StoreKind.PLAY, WEST.lat() + 0.0010, WEST.lng(), 99),
			candidate(2, StoreKind.PLAY, WEST.lat() + 0.0030, WEST.lng(), 98),
			candidate(3, StoreKind.PLAY, WEST.lat() + 0.0040, WEST.lng(), 97))));
		CandidatePool.LegCandidates leg = pool.forLeg(slot, 0, 500, 100);
		GeoPoint earlierStop = new GeoPoint(WEST.lat() + 0.0010, WEST.lng());

		// when
		List<CandidatePool.LegCandidates.Ranked> nearby = leg.near(WEST, List.of(earlierStop), Set.of());

		// then
		assertThat(nearby).extracting(row -> row.selected().candidate().place().id()).containsExactly(2L, 3L);
		assertThat(nearby).extracting(CandidatePool.LegCandidates.Ranked::rank).containsExactly(1, 2);
	}

	@Test
	@DisplayName("후보가 35곳이고 앞의 5곳을 제외함 → 30곳을 주고 순번은 6번부터 35번까지다")
	void limitsToThirtyAfterExclusions() {
		// given
		CourseSchedule.CourseSlot slot = CourseSchedule.afternoonPlay(Weekday.FRI);
		List<Candidate> candidates = new ArrayList<>();
		for (int id = 1; id <= 35; id++) {
			candidates.add(candidate(id, StoreKind.PLAY, WEST.lat() + 0.0005, WEST.lng(), 99.9 - id * 0.1));
		}
		CandidatePool pool = new CandidatePool(CONTEXT, Map.of(slot.slot(), candidates));
		CandidatePool.LegCandidates leg = pool.forLeg(slot, 0, 500, 0);

		// when
		List<CandidatePool.LegCandidates.Ranked> nearby = leg.near(WEST, List.of(), Set.of(1L, 2L, 3L, 4L, 5L));

		// then
		assertThat(nearby).hasSize(30);
		assertThat(nearby.getFirst().rank()).isEqualTo(6);
		assertThat(nearby.getLast().rank()).isEqualTo(35);
	}
}
