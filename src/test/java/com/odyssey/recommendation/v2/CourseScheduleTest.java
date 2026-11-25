package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseSchedule.Requirement;
import com.odyssey.store.entity.StoreKind;

class CourseScheduleTest {

	@ParameterizedTest
	@CsvSource({"REQUIRED, false", "OPTIONAL, true", "OPT_IN, true"})
	@DisplayName("자리를 채우지 못함 → 선택 자리는 건너뛰고 필수 자리는 중단한다")
	void skipWhenMissing(Requirement requirement, boolean expected) {
		// when
		boolean skip = requirement.skipWhenMissing();

		// then
		assertThat(skip).isEqualTo(expected);
	}

	private static List<Integer> positions(TimeSlot start, boolean lateNight) {
		return CourseSchedule.slots(Weekday.MON, StoreKind.CAFE, start, lateNight).stream()
			.map(CourseSlot::position).toList();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"LUNCH | false | 1,2,3,4,5",
		"LUNCH | true | 1,2,3,4,5,6",
		"AFTERNOON | false | 2,3,4,5",
		"AFTERNOON | true | 2,3,4,5,6",
		"DINNER | false | 4,5",
		"DINNER | true | 4,5,6",
		"LATE_NIGHT | true | 6"})
	@DisplayName("시작 시간대와 심야 선택 → 시작 자리부터 이어지고 심야 자리는 선택했을 때만 든다")
	void slotsFromStart(TimeSlot start, boolean lateNight, String expected) {
		// when
		List<Integer> positions = positions(start, lateNight);

		// then
		assertThat(positions.stream().map(String::valueOf).toList()).containsExactly(expected.split(","));
	}

	@Test
	@DisplayName("심야에 시작하고 심야를 선택하지 않음 → 자리가 하나도 없다")
	void lateNightStartWithoutLateNight() {
		// when
		List<Integer> positions = positions(TimeSlot.LATE_NIGHT, false);

		// then
		assertThat(positions).isEmpty();
	}

	@Test
	@DisplayName("여섯 자리 → 필수는 1·2·4번, 선택은 3·5번, 요청이 있을 때만 넣는 자리는 6번이다")
	void requirementByPosition() {
		// when
		List<CourseSlot> slots = CourseSchedule.slots(Weekday.MON, StoreKind.CAFE, TimeSlot.LUNCH, true);

		// then
		assertThat(slots).extracting(CourseSlot::requirement).containsExactly(Requirement.REQUIRED,
			Requirement.REQUIRED, Requirement.OPTIONAL, Requirement.REQUIRED, Requirement.OPTIONAL,
			Requirement.OPT_IN);
	}

	@Test
	@DisplayName("평일 여섯 자리 → 자리마다 받는 종류와 방문 백분위 하한이 정해진 값이다")
	void kindsAndMinimumsOnWeekday() {
		// when
		List<CourseSlot> slots = CourseSchedule.slots(Weekday.FRI, StoreKind.CAFE, TimeSlot.LUNCH, true);

		// then
		assertThat(slots).extracting(CourseSlot::kinds).containsExactly(
			Set.of(StoreKind.KOREAN), Set.of(StoreKind.CAFE), Set.of(StoreKind.PLAY, StoreKind.SHOP),
			Set.of(StoreKind.KOREAN), Set.of(StoreKind.PLAY), Set.of(StoreKind.BAR));
		assertThat(slots).extracting(CourseSlot::minPercentile).containsExactly(75.0, 75.0, 60.0, 75.0, 75.0, 75.0);
	}

	@Test
	@DisplayName("주말 여섯 자리 → 3번 자리도 하한이 75 라서 모든 자리가 75 다")
	void minimumsOnWeekend() {
		// when
		List<CourseSlot> slots = CourseSchedule.slots(Weekday.SAT, StoreKind.CAFE, TimeSlot.LUNCH, true);

		// then
		assertThat(slots).extracting(CourseSlot::minPercentile).containsExactly(75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
	}

	@Test
	@DisplayName("오후를 카페로 고름 → 2번 자리는 카페만 받고 80분 머문다")
	void afternoonCafe() {
		// when
		CourseSlot slot = CourseSchedule.slots(Weekday.MON, StoreKind.CAFE, TimeSlot.AFTERNOON, false).getFirst();

		// then
		assertThat(slot.kinds()).containsExactly(StoreKind.CAFE);
		assertThat(slot.stayMinutes(StoreKind.CAFE)).isEqualTo(80);
	}

	@Test
	@DisplayName("오후를 구경으로 고름 → 2번 자리는 쇼핑만 받고 30분 머문다")
	void afternoonShop() {
		// when
		CourseSlot slot = CourseSchedule.slots(Weekday.MON, StoreKind.SHOP, TimeSlot.AFTERNOON, false).getFirst();

		// then
		assertThat(slot.kinds()).containsExactly(StoreKind.SHOP);
		assertThat(slot.stayMinutes(StoreKind.SHOP)).isEqualTo(30);
	}

	@Test
	@DisplayName("오후 자리가 카페·구경이 아님 → IllegalArgumentException")
	void afternoonKindRejected() {
		// when & then
		assertThatThrownBy(() -> CourseSchedule.slots(Weekday.MON, StoreKind.KOREAN, TimeSlot.LUNCH, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("afternoon");
	}

	@ParameterizedTest
	@EnumSource(value = Weekday.class, names = {"MON", "TUE", "WED", "THU", "FRI"})
	@DisplayName("평일 오후 놀거리·쇼핑 자리 → 방문 백분위 60 이상부터 받는다")
	void afternoonPlayWeekday(Weekday weekday) {
		// when
		CourseSlot slot = CourseSchedule.afternoonPlay(weekday);

		// then
		assertThat(slot.minPercentile()).isEqualTo(60.0);
	}

	@ParameterizedTest
	@EnumSource(value = Weekday.class, names = {"SAT", "SUN"})
	@DisplayName("주말 오후 놀거리·쇼핑 자리 → 목록 API 와 같은 기본 하한을 쓴다")
	void afternoonPlayWeekend(Weekday weekday) {
		// when
		CourseSlot slot = CourseSchedule.afternoonPlay(weekday);

		// then
		assertThat(slot.minPercentile()).isEqualTo(75.0);
	}

	@Test
	@DisplayName("3번 자리 → 놀거리는 60분, 쇼핑은 30분 머문다")
	void afternoonPlayStays() {
		// when
		CourseSlot slot = CourseSchedule.afternoonPlay(Weekday.SAT);

		// then
		assertThat(slot.kinds()).containsExactlyInAnyOrder(StoreKind.PLAY, StoreKind.SHOP);
		assertThat(slot.stayMinutes(StoreKind.PLAY)).isEqualTo(60);
		assertThat(slot.stayMinutes(StoreKind.SHOP)).isEqualTo(30);
	}

	@ParameterizedTest
	@EnumSource(value = StoreKind.class, names = {"CAFE", "SHOP"})
	@DisplayName("어느 종류로 골라도 → 각 자리는 머문 뒤 나오는 시각이 다음 자리 도착 시각을 넘지 않는다")
	void staysFitBeforeNextSlot(StoreKind afternoon) {
		// given
		List<CourseSlot> slots = CourseSchedule.slots(Weekday.SAT, afternoon, TimeSlot.LUNCH, true);

		// when & then
		for (int i = 0; i < slots.size() - 1; i++) {
			CourseSlot current = slots.get(i);
			LocalTime nextVisit = slots.get(i + 1).visitTime();
			for (StoreKind kind : current.kinds()) {
				LocalTime leave = current.visitTime().plusMinutes(current.stayMinutes(kind));
				assertThat(leave).as("%d번 자리 %s", current.position(), kind).isBeforeOrEqualTo(nextVisit);
			}
		}
	}

	@Test
	@DisplayName("일정에 없는 종류의 머무는 시간을 물음 → IllegalArgumentException")
	void unknownKindStay() {
		// given
		CourseSlot slot = CourseSchedule.slots(Weekday.MON, StoreKind.CAFE, TimeSlot.LUNCH, false).getFirst();

		// when & then
		assertThatThrownBy(() -> slot.stayMinutes(StoreKind.BAR))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("BAR");
	}

	@Test
	@DisplayName("받는 종류가 비어 있음 → 자리를 만들 수 없다")
	void emptyKindsRejected() {
		// when & then
		assertThatThrownBy(() -> new CourseSlot(1, TimeSlot.LUNCH, LocalTime.NOON, Map.of(),
			Requirement.REQUIRED, 75))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("자리를 만든 뒤 원본 종류 표를 바꿈 → 자리의 종류는 바뀌지 않는다")
	void stayByKindIsCopied() {
		// given
		Map<StoreKind, Integer> stays = new java.util.HashMap<>(Map.of(StoreKind.KOREAN, 50));
		CourseSlot slot = new CourseSlot(1, TimeSlot.LUNCH, LocalTime.NOON, stays, Requirement.REQUIRED, 75);

		// when
		stays.put(StoreKind.BAR, 70);

		// then
		assertThat(slot.kinds()).isEqualTo(Set.of(StoreKind.KOREAN));
	}
}
