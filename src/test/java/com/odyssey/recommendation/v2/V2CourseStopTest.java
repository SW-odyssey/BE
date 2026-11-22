package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.store.entity.StoreKind;

/** {@link Stop#of} 의 나오는 시각과 도보 분 계산. */
class V2CourseStopTest {

	private static Item place(double distanceMeters) {
		return new Item(1, "매장1", "11", StoreKind.BAR, 37.566, 126.992, distanceMeters, "place-1");
	}

	@Test
	@DisplayName("22:30 에 70분 머문다 → 나오는 시각은 23:40 이다")
	void leaveTime() {
		// when
		Stop stop = Stop.of(TimeSlot.LATE_NIGHT, LocalTime.of(22, 30), 70, 1, place(70), false);

		// then
		assertThat(stop.visitTime()).isEqualTo("22:30");
		assertThat(stop.leaveTime()).isEqualTo("23:40");
	}

	@Test
	@DisplayName("자정을 넘겨 머문다 → 나오는 시각은 00:00 부터 다시 센다")
	void leaveTimeWrapsAtMidnight() {
		// when
		Stop stop = Stop.of(TimeSlot.LATE_NIGHT, LocalTime.of(23, 30), 70, 1, place(70), false);

		// then
		assertThat(stop.leaveTime()).isEqualTo("00:40");
	}

	@Test
	@DisplayName("직선 70m → 도보 2분이다")
	void walkMinutes() {
		// when
		Stop stop = Stop.of(TimeSlot.LATE_NIGHT, LocalTime.of(22, 30), 70, 1, place(70), false);

		// then
		assertThat(stop.walkMinutes()).isEqualTo(2);
	}

	@Test
	@DisplayName("직선 0m → 도보 1분이다")
	void walkMinutesAtLeastOne() {
		// when
		int minutes = Stop.walkMinutes(0);

		// then
		assertThat(minutes).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({"51, 1", "52, 2", "103, 2", "104, 3"})
	@DisplayName("직선거리가 분 단위 경계 근처다 → 직선 x 1.3 을 분당 67m 로 나눠 올림한다")
	void walkMinutesAroundBoundaries(double distanceMeters, int expected) {
		// when
		int minutes = Stop.walkMinutes(distanceMeters);

		// then
		assertThat(minutes).isEqualTo(expected);
	}

	@Test
	@DisplayName("이유를 붙인다 → 이유만 바뀌고 시각·도보 분·장소는 그대로다")
	void withReasonsKeepsOtherFields() {
		// given
		Stop stop = Stop.of(TimeSlot.LATE_NIGHT, LocalTime.of(22, 30), 70, 3, place(70), true);

		// when
		Stop withReasons = stop.withReasons(List.of(new PeakNow()));

		// then
		assertThat(withReasons.reasons()).containsExactly(new PeakNow());
		assertThat(withReasons).usingRecursiveComparison().ignoringFields("reasons").isEqualTo(stop);
	}

	@Test
	@DisplayName("이유 목록을 만든 뒤 원본 목록을 바꾼다 → 정류장의 이유는 바뀌지 않는다")
	void reasonsAreCopied() {
		// given
		List<CourseReason> reasons = new ArrayList<>(List.of(new PeakNow()));
		Stop stop = new Stop(TimeSlot.LUNCH, "12:30", "13:20", 2, 1, place(70), false, reasons);

		// when
		reasons.add(new LocalTop(3));

		// then
		assertThat(stop.reasons()).containsExactly(new PeakNow());
	}
}
