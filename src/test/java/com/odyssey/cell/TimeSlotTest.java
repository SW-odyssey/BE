package com.odyssey.cell;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeSlotTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
		"11:00, LUNCH", "12:59, LUNCH",
		"14:00, AFTERNOON", "16:59, AFTERNOON",
		"18:00, DINNER", "20:59, DINNER",
		"22:00, LATE_NIGHT", "23:59, LATE_NIGHT", "00:00, LATE_NIGHT", "00:59, LATE_NIGHT"})
	@DisplayName("시간대 안의 시각 → 그 시간대를 준다. 시작 시각은 포함하고 끝 시각은 포함하지 않는다")
	void timeInsideSlot(String time, TimeSlot expected) {
		assertThat(TimeSlot.at(LocalTime.parse(time))).contains(expected);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"10:59", "13:00", "13:30", "13:59", "17:00", "17:30", "21:00", "21:30", "01:00", "01:30", "10:00"})
	@DisplayName("시간대 사이나 심야가 끝난 뒤의 시각 → 시간대가 없다")
	void timeOutsideAnySlot(String time) {
		assertThat(TimeSlot.at(LocalTime.parse(time))).isEmpty();
	}
}
