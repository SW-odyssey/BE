package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseSelector.Pick;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.store.entity.StoreKind;

/** {@link CourseSelector} 의 요청·선택 값이 제외 매장과 자리를 어떻게 다루는지 확인한다. */
class CourseSelectorTest {

	private static final List<CourseSlot> SLOTS = BeamSelectorTest.SLOTS;

	private static Request request(Set<Long> excludeIds, Map<Integer, Set<Long>> excludedBySlot) {
		return new Request(SLOTS, BeamSelectorTest.START, excludeIds, Weekday.FRI, 7, WalkPreference.DEFAULT,
			excludedBySlot);
	}

	@Test
	@DisplayName("자리별 제외가 없음 → 어느 자리든 모든 자리에서 뺄 매장만 준다")
	void excludedAtWithoutSlotExclusions() {
		// given
		Request request = request(Set.of(1L, 2L), Map.of());

		// when
		Set<Long> excluded = request.excludedAt(4);

		// then
		assertThat(excluded).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	@DisplayName("4번 자리에서만 3번을 뺌 → 4번 자리는 둘을 합치고 다른 자리는 모든 자리에서 뺄 매장만 준다")
	void excludedAtMergesOnlyForThatSlot() {
		// given
		Request request = request(Set.of(1L), Map.of(4, Set.of(3L)));

		// then
		assertThat(request.excludedAt(4)).containsExactlyInAnyOrder(1L, 3L);
		assertThat(request.excludedAt(1)).containsExactly(1L);
	}

	@Test
	@DisplayName("자리별 제외를 합침 → 요청이 가진 제외 목록은 바뀌지 않는다")
	void excludedAtDoesNotChangeRequest() {
		// given
		Request request = request(Set.of(1L), Map.of(4, Set.of(3L)));

		// when
		request.excludedAt(4);

		// then
		assertThat(request.excludeIds()).containsExactly(1L);
		assertThat(request.excludedBySlot().get(4)).containsExactly(3L);
	}

	@Test
	@DisplayName("요청을 만든 뒤 원본 제외 목록을 바꿈 → 요청의 제외 목록은 그대로다")
	void requestCopiesExclusions() {
		// given
		Set<Long> ids = new HashSet<>(Set.of(1L));
		Set<Long> closed = new HashSet<>(Set.of(3L));
		Request request = request(ids, Map.of(4, closed));

		// when
		ids.add(2L);
		closed.add(5L);

		// then
		assertThat(request.excludeIds()).containsExactly(1L);
		assertThat(request.excludedAt(4)).containsExactlyInAnyOrder(1L, 3L);
	}

	@Test
	@DisplayName("자리별 제외 없이 만든 요청 → 자리별 제외가 비어 있다")
	void shortConstructorHasNoSlotExclusions() {
		// when
		Request request = new Request(SLOTS, BeamSelectorTest.START, Set.of(), Weekday.FRI, 7, WalkPreference.DEFAULT);

		// then
		assertThat(request.excludedBySlot()).isEmpty();
	}

	@Test
	@DisplayName("일정에 있는 자리 번호 → 그 자리를 주고, 없는 번호 → NoSuchElementException")
	void slotAt() {
		// given
		Request request = request(Set.of(), Map.of());

		// then
		assertThat(request.slotAt(4).position()).isEqualTo(4);
		assertThatThrownBy(() -> request.slotAt(9)).isInstanceOf(NoSuchElementException.class);
	}

	@Test
	@DisplayName("고른 후보 → 자리 번호·시각과 후보 순번(rank)을 담은 정류장을 만든다")
	void chosenPick() {
		// given
		CourseSlot slot = SLOTS.get(3);
		Item place = Item.from(new V2RecommendationSelector.Selected(
			BeamSelectorTest.store(8, StoreKind.KOREAN, 0, 97), 0));

		// when
		Pick pick = Pick.chosen(slot, 5, place, true, 4, 97);

		// then
		assertThat(pick.stop().position()).isEqualTo(4);
		assertThat(pick.stop().visitTime()).isEqualTo("18:00");
		assertThat(pick.stop().leaveTime()).isEqualTo("19:00");
		assertThat(pick.stop().candidateRank()).isEqualTo(5);
		assertThat(pick.stop().hoursUnknown()).isTrue();
		assertThat(pick.bandSize()).isEqualTo(4);
		assertThat(pick.percentile()).isEqualTo(97);
		assertThat(pick.reason()).isNull();
	}

	@Test
	@DisplayName("못 고른 자리 → 정류장 없이 이유만 담고 점수대·백분위는 0 이다")
	void stoppedPick() {
		// when
		Pick pick = Pick.stopped("NO_VERIFIED_MATCH");

		// then
		assertThat(pick.stop()).isNull();
		assertThat(pick.reason()).isEqualTo("NO_VERIFIED_MATCH");
		assertThat(pick.bandSize()).isZero();
		assertThat(pick.percentile()).isZero();
	}
}
