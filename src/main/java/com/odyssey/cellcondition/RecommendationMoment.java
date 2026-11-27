package com.odyssey.cellcondition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.odyssey.cell.TimeSlot;
import com.odyssey.cellcondition.entity.Weekday;

/**
 * 추천 데이터를 읽는 기준이 되는 요일과 시간대.
 *
 * <p>자정부터 심야가 끝나기 전까지는 전날의 심야다. 토요일 00:30 은 금요일 심야로 읽는다.
 */
public record RecommendationMoment(Weekday weekday, TimeSlot slot) {

	/** 시간대에 들지 않는 시각이면 추천 데이터가 없으므로 비어 있다. */
	public static Optional<RecommendationMoment> at(LocalDateTime dateTime) {
		return TimeSlot.at(dateTime.toLocalTime())
			.map(slot -> new RecommendationMoment(weekdayOf(dateTime, slot), slot));
	}

	private static Weekday weekdayOf(LocalDateTime dateTime, TimeSlot slot) {
		boolean afterMidnightOfLateNight = slot.endsNextDay()
			&& dateTime.toLocalTime().isBefore(slot.endTimeExclusive());
		LocalDate dataDay = afterMidnightOfLateNight ? dateTime.toLocalDate().minusDays(1) : dateTime.toLocalDate();
		return Weekday.valueOf(dataDay.getDayOfWeek().name().substring(0, 3));
	}
}
