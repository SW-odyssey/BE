package com.odyssey.cellcondition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cell.TimeSlot;
import com.odyssey.cellcondition.entity.Weekday;

class RecommendationMomentTest {

	@Test
	@DisplayName("토요일 00:30 → 금요일 심야로 읽는다")
	void saturdayAfterMidnightIsFridayLateNight() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 28, 0, 30)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("토요일 00:00 → 금요일 심야다")
	void midnightBelongsToPreviousDay() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 28, 0, 0)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("토요일 00:59 → 아직 금요일 심야다")
	void lastMinuteOfLateNight() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 28, 0, 59)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("토요일 01:00 → 심야가 아니므로 추천 데이터가 없다")
	void oneAmIsOutsideLateNight() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 28, 1, 0))).isEmpty();
	}

	@Test
	@DisplayName("금요일 23:30 → 같은 날 금요일 심야다")
	void beforeMidnightStaysOnSameDay() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 27, 23, 30)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("월요일 00:30 → 일요일 심야로 주를 넘겨 읽는다")
	void mondayAfterMidnightIsSundayLateNight() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 30, 0, 30)))
			.contains(new RecommendationMoment(Weekday.SUN, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("금요일 12:59 → 금요일 점심이다")
	void justBeforeLunchEnds() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 27, 12, 59)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.LUNCH));
	}

	@Test
	@DisplayName("금요일 13:00 과 13:30 → 해당하는 시간대가 없다")
	void betweenLunchAndAfternoon() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 27, 13, 0))).isEmpty();
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 27, 13, 30))).isEmpty();
	}

	@Test
	@DisplayName("금요일 14:00 → 금요일 오후다")
	void afternoonStartsAtFourteen() {
		assertThat(RecommendationMoment.at(LocalDateTime.of(2026, 11, 27, 14, 0)))
			.contains(new RecommendationMoment(Weekday.FRI, TimeSlot.AFTERNOON));
	}
}
