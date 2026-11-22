package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellslotscore.entity.TimeSlot;
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
		Stop stop = Stop.of(6, TimeSlot.LATE_NIGHT, LocalTime.of(22, 30), 70, 1, place(70), false);

		// then
		assertThat(stop.visitTime()).isEqualTo("22:30");
		assertThat(stop.leaveTime()).isEqualTo("23:40");
	}

	@Test
	@DisplayName("자정을 넘겨 머문다 → 나오는 시각은 00:00 부터 다시 센다")
	void leaveTimeWrapsAtMidnight() {
		// when
		Stop stop = Stop.of(6, TimeSlot.LATE_NIGHT, LocalTime.of(23, 30), 70, 1, place(70), false);

		// then
		assertThat(stop.leaveTime()).isEqualTo("00:40");
	}

	@Test
	@DisplayName("직선 70m → 도보 2분이다")
	void walkMinutes() {
		// when
		Stop stop = Stop.of(6, TimeSlot.LATE_NIGHT, LocalTime.of(22, 30), 70, 1, place(70), false);

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
}
