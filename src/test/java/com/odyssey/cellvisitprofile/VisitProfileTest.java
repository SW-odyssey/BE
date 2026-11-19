package com.odyssey.cellvisitprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.VisitProfile.VisitBar;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;

/** {@link VisitProfile} 의 막대 불변식과 강조 시간대를 확인한다. DB 없이 값만 본다. */
class VisitProfileTest {

	private static List<VisitBar> bars(int lunch, int afternoon, int dinner, int lateNight) {
		return List.of(
			new VisitBar(TimeSlot.LUNCH, lunch), new VisitBar(TimeSlot.AFTERNOON, afternoon),
			new VisitBar(TimeSlot.DINNER, dinner), new VisitBar(TimeSlot.LATE_NIGHT, lateNight));
	}

	static Stream<List<VisitBar>> corruptBars() {
		return Stream.of(
			// 막대가 3개
			List.of(new VisitBar(TimeSlot.LUNCH, 100), new VisitBar(TimeSlot.AFTERNOON, 50),
				new VisitBar(TimeSlot.DINNER, 20)),
			// 막대가 없음
			List.of(),
			// 100 이 둘
			bars(100, 100, 20, 0),
			// 100 이 없음
			bars(90, 50, 20, 0),
			// 같은 시간대가 두 번
			List.of(new VisitBar(TimeSlot.LUNCH, 100), new VisitBar(TimeSlot.LUNCH, 50),
				new VisitBar(TimeSlot.DINNER, 20), new VisitBar(TimeSlot.LATE_NIGHT, 10)));
	}

	@Test
	@DisplayName("AVAILABLE 이고 막대 넷 중 100 이 하나다 → 만들어지고 막대를 받은 순서대로 가진다")
	void availableKeepsBarOrder() {
		// when
		VisitProfile profile =
			new VisitProfile(VisitProfileStatus.AVAILABLE, bars(70, 75, 100, 70));

		// then
		assertThat(profile.bars()).extracting(VisitBar::slot).containsExactly(TimeSlot.LUNCH,
			TimeSlot.AFTERNOON, TimeSlot.DINNER, TimeSlot.LATE_NIGHT);
		assertThat(profile.bars()).extracting(VisitBar::relativeHeight)
			.containsExactly(70, 75, 100, 70);
	}

	@Test
	@DisplayName("막대 합이 100 이 아니다 → 오류가 아니다")
	void sumNeedNotBeHundred() {
		// when & then: 70+75+100+70 = 315
		assertThat(new VisitProfile(VisitProfileStatus.AVAILABLE, bars(70, 75, 100, 70)).bars())
			.hasSize(4);
	}

	@Test
	@DisplayName("100 인 막대가 있다 → 그 시간대가 강조 시간대다")
	void peakSlot() {
		// when
		VisitProfile profile =
			new VisitProfile(VisitProfileStatus.AVAILABLE, bars(25, 100, 40, 10));

		// then
		assertThat(profile.peakSlot()).contains(TimeSlot.AFTERNOON);
	}

	@ParameterizedTest
	@MethodSource("corruptBars")
	@DisplayName("AVAILABLE 인데 막대가 깨져 있다(3개 · 없음 · 100 이 둘 · 100 이 없음 · 시간대 중복) → 예외")
	void availableWithCorruptBars(List<VisitBar> bars) {
		// when
		IllegalStateException exception = catchThrowableOfType(IllegalStateException.class,
			() -> new VisitProfile(VisitProfileStatus.AVAILABLE, bars));

		// then
		assertThat(exception).hasMessageContaining("Invalid published visit profile");
	}

	@ParameterizedTest
	@EnumSource(value = VisitProfileStatus.class, names = "AVAILABLE",
		mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("그래프를 그리지 않는 상태에 막대가 없다 → 만들어지고 강조 시간대도 없다")
	void nonAvailableWithoutBars(VisitProfileStatus status) {
		// when
		VisitProfile profile = new VisitProfile(status, List.of());

		// then
		assertThat(profile.bars()).isEmpty();
		assertThat(profile.peakSlot()).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = VisitProfileStatus.class, names = "AVAILABLE",
		mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("그래프를 그리지 않는 상태인데 막대가 있다 → 예외")
	void nonAvailableWithBars(VisitProfileStatus status) {
		// when
		IllegalStateException exception = catchThrowableOfType(IllegalStateException.class,
			() -> new VisitProfile(status, bars(70, 75, 100, 70)));

		// then
		assertThat(exception).hasMessageContaining("Invalid published visit profile");
	}

	@ParameterizedTest
	@EnumSource(VisitProfileStatus.class)
	@DisplayName("그래프를 그리는 상태는 AVAILABLE 하나뿐이다")
	void onlyAvailableShowsGraph(VisitProfileStatus status) {
		// then
		assertThat(status.showsGraph()).isEqualTo(status == VisitProfileStatus.AVAILABLE);
	}
}
