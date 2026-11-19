package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/** {@link V2RecommendationSelector} 의 반경, 정렬, 개수 제한을 mock 없이 확인한다. */
class V2RecommendationSelectorTest {

	private static final double ORIGIN_LAT = 37.5655;
	private static final double ORIGIN_LNG = 126.9905;
	/** 같은 경도에서 위도 1도가 가리키는 거리. 위도 차이로 원하는 거리를 만든다. */
	private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_000;

	private static final V2RecommendationQuery ALL = query(null, null, "");

	private static V2RecommendationQuery query(Category category, StoreKind kind, String q) {
		return new V2RecommendationQuery("nov", Weekday.MON, TimeSlot.LUNCH,
			new GeoPoint(ORIGIN_LAT, ORIGIN_LNG), category, kind, q);
	}

	/** 사용자 위치에서 정북쪽으로 meters 만큼 떨어진 후보를 만든다. */
	private static Candidate candidate(long id, String name, StoreKind kind, double meters,
		double percentile, String mgmtNo) {
		Place place = new Place(id, name, 100 + id, kind, ORIGIN_LAT + meters / METERS_PER_DEGREE,
			ORIGIN_LNG, null, "place-" + id);
		return new Candidate(place, mgmtNo, percentile);
	}

	private static Candidate candidate(long id, double meters, double percentile, String mgmtNo) {
		return candidate(id, "매장" + id, StoreKind.KOREAN, meters, percentile, mgmtNo);
	}

	private static List<Long> ids(List<Selected> selected) {
		return selected.stream().map(s -> s.candidate().place().id()).toList();
	}

	@Test
	@DisplayName("반경 300m 안(299.9m)에 있다 → 고르고, 밖(300.1m)에 있다 → 뺀다")
	void radiusBoundary() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, 299.9, 90, "a"),
			candidate(2, 300.1, 90, "b"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(1L);
	}

	@Test
	@DisplayName("백분위 98 인 200m 매장과 81 인 30m 매장 → 백분위가 높은 쪽이 먼저 온다")
	void higherPercentileFirstEvenWhenFarther() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, 30, 81, "a"),
			candidate(2, 200, 98, "b"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("백분위가 같다 → 가까운 매장이 먼저 온다")
	void samePercentileOrderedByDistance() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, 200, 90, "a"),
			candidate(2, 50, 90, "b"),
			candidate(3, 120, 90, "c"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("백분위와 거리가 같다 → 관리번호 순으로 정한다")
	void tieBrokenByManagementNumber() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, 100, 90, "mgmt-c"),
			candidate(2, 100, 90, "mgmt-a"),
			candidate(3, 100, 90, "mgmt-b"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("반올림하면 거리가 같아지는 매장 → 반올림 전 거리로 먼저 가까운 쪽이 앞선다")
	void sortsByDistanceBeforeRounding() {
		// given: 10.04m 와 10.01m 는 둘 다 10.0 으로 반올림되지만 관리번호는 반대 순서다
		List<Candidate> candidates = List.of(
			candidate(1, 10.04, 90, "mgmt-a"),
			candidate(2, 10.01, 90, "mgmt-b"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("입력을 기대 순서의 역순으로 넣는다 → 같은 순서로 고른다")
	void orderDoesNotDependOnInputOrder() {
		// given
		List<Candidate> inOrder = List.of(
			candidate(1, 50, 98, "a"),
			candidate(2, 20, 90, "b"),
			candidate(3, 80, 90, "c"),
			candidate(4, 10, 81, "d"));
		List<Candidate> reversed = new ArrayList<>(inOrder);
		Collections.reverse(reversed);

		// when
		List<Long> fromInOrder = ids(V2RecommendationSelector.select(inOrder, ALL));
		List<Long> fromReversed = ids(V2RecommendationSelector.select(reversed, ALL));

		// then
		assertThat(fromInOrder).containsExactly(1L, 2L, 3L, 4L);
		assertThat(fromReversed).isEqualTo(fromInOrder);
	}

	@Test
	@DisplayName("후보가 31곳이다 → 우선순위가 가장 낮은 1곳을 빼고 30곳을 준다")
	void limitsToThirty() {
		// given: 백분위 100 부터 70 까지 31곳. 가장 낮은 70 이 빠져야 한다
		List<Candidate> candidates = IntStream.rangeClosed(1, 31)
			.mapToObj(i -> candidate(i, 100, 101 - i, "mgmt-" + i))
			.toList();

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(selected).hasSize(30);
		assertThat(ids(selected)).doesNotContain(31L);
		assertThat(ids(selected).getFirst()).isEqualTo(1L);
	}

	@Test
	@DisplayName("같은 칸의 매장이 여러 곳이다 → 각각 한 자리를 차지한다")
	void sameCellStoresTakeSeparateSlots() {
		// given: 같은 칸(cellId 101)의 매장 둘과 다른 칸 하나
		Place first = new Place(1, "첫째", 101, StoreKind.KOREAN, ORIGIN_LAT, ORIGIN_LNG, null, "p1");
		Place second = new Place(2, "둘째", 101, StoreKind.BAR, ORIGIN_LAT, ORIGIN_LNG, null, "p2");
		List<Candidate> candidates = List.of(
			new Candidate(first, "a", 90),
			new Candidate(second, "b", 90),
			candidate(3, 50, 80, "c"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("선택한 매장 → 사용자 위치에서의 거리를 반올림하지 않고 담는다")
	void keepsUnroundedDistance() {
		// given
		List<Candidate> candidates = List.of(candidate(1, 12.34, 90, "a"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(selected.getFirst().distanceMeters()).isCloseTo(12.34, within(0.001));
	}

	@Test
	@DisplayName("후보가 하나도 없다 → 빈 목록을 준다")
	void emptyCandidates() {
		assertThat(V2RecommendationSelector.select(List.of(), ALL)).isEmpty();
	}

	@Test
	@DisplayName("후보 목록이 바꿀 수 없는 목록이다 → 입력을 바꾸지 않고 고른다")
	void doesNotMutateInput() {
		// given: List.of 는 정렬하려 하면 예외를 던진다
		List<Candidate> candidates = List.of(
			candidate(1, 50, 80, "a"),
			candidate(2, 50, 90, "b"));

		// when
		List<Selected> selected = V2RecommendationSelector.select(candidates, ALL);

		// then
		assertThat(ids(selected)).containsExactly(2L, 1L);
		assertThat(candidates).extracting(c -> c.place().id()).containsExactly(1L, 2L);
	}

	private static List<Candidate> mixedKinds() {
		return List.of(
			candidate(1, "한식당", StoreKind.KOREAN, 10, 90, "a"),
			candidate(2, "술집", StoreKind.BAR, 20, 90, "b"),
			candidate(3, "기타식당", StoreKind.OTHER, 30, 90, "c"),
			candidate(4, "스타카페", StoreKind.CAFE, 40, 90, "d"));
	}

	@Test
	@DisplayName("대분류가 food 다 → 카페는 빼고 식당과 술집만 고른다")
	void foodCategory() {
		// when
		List<Selected> selected = V2RecommendationSelector.select(mixedKinds(),
			query(Category.FOOD, null, ""));

		// then
		assertThat(ids(selected)).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("대분류가 cafe 다 → 카페만 고른다")
	void cafeCategory() {
		// when
		List<Selected> selected = V2RecommendationSelector.select(mixedKinds(),
			query(Category.CAFE, null, ""));

		// then
		assertThat(ids(selected)).containsExactly(4L);
	}

	@Test
	@DisplayName("대분류가 조건 없음이다 → 분류와 상관없이 모두 고른다")
	void allCategory() {
		// when
		List<Selected> selected = V2RecommendationSelector.select(mixedKinds(), ALL);

		// then
		assertThat(ids(selected)).containsExactly(1L, 2L, 3L, 4L);
	}

	@Test
	@DisplayName("가게 분류를 주었다 → 그 분류만 남긴다")
	void kindFilter() {
		// when
		List<Selected> selected = V2RecommendationSelector.select(mixedKinds(),
			query(null, StoreKind.BAR, ""));

		// then
		assertThat(ids(selected)).containsExactly(2L);
	}

	@Test
	@DisplayName("검색어를 주었다 → 이름에 포함된 매장만 남기고 대소문자는 구분하지 않는다")
	void nameSearchIgnoresCase() {
		// given
		List<Candidate> candidates = List.of(
			candidate(1, "Onion Cafe", StoreKind.CAFE, 10, 90, "a"),
			candidate(2, "스타카페", StoreKind.CAFE, 20, 90, "b"),
			candidate(3, "스타식당", StoreKind.KOREAN, 30, 90, "c"));

		// when & then
		assertThat(ids(V2RecommendationSelector.select(candidates, query(null, null, "ONION"))))
			.containsExactly(1L);
		assertThat(ids(V2RecommendationSelector.select(candidates, query(null, null, "onion"))))
			.containsExactly(1L);
		assertThat(ids(V2RecommendationSelector.select(candidates, query(null, null, "스타"))))
			.containsExactly(2L, 3L);
	}

	@Test
	@DisplayName("검색어에 맞는 매장이 없다 → 빈 목록을 준다")
	void nameSearchWithoutMatch() {
		// when
		List<Selected> selected = V2RecommendationSelector.select(mixedKinds(),
			query(null, null, "없는이름"));

		// then
		assertThat(selected).isEmpty();
	}
}
