package com.odyssey.recommendation.v2;

import java.time.LocalTime;
import java.util.List;

import com.odyssey.cellcondition.entity.Weekday;

/**
 * Google 영업시간은 일요일을 0으로 세며 한 영업 구간이 토요일에서 일요일까지 이어질 수 있다.
 */
final class WeeklyOpeningHours {

	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	/** @param close 없으면 24시간 영업이다 */
	record Period(Point open, Point close) {
		boolean coversStay(int arrivalMinute, int stayMinutes) {
			if (open == null) {
				return false;
			}
			if (close == null) {
				return true;
			}
			int openingMinute = minuteOfWeek(open);
			int closingMinute = minuteOfWeek(close);
			if (closingMinute <= openingMinute) {
				closingMinute += MINUTES_PER_WEEK;
			}
			return stayFitsBetween(openingMinute, closingMinute, arrivalMinute, stayMinutes)
				|| stayFitsBetween(openingMinute, closingMinute, arrivalMinute + MINUTES_PER_WEEK, stayMinutes);
		}

		private static boolean stayFitsBetween(int open, int close, int arrival, int stayMinutes) {
			return open <= arrival && arrival < close && arrival + stayMinutes <= close;
		}
	}

	/** @param day 0(일)~6(토) */
	record Point(int day, int hour, int minute) {
	}

	private WeeklyOpeningHours() {
	}

	/**
	 * 도착 시각이 한 영업 구간 안이고 머무는 시간이 끝날 때까지 그 구간이 이어지는지 본다. 마감 정각
	 * 도착은 영업시간 밖이고, 마감 정각에 나오는 것은 허용한다. 맞붙은 두 구간(예: 15시 마감·15시 재개)을
	 * 이어 보지는 않는다.
	 *
	 * @param periods 영업 구간. null 이면 영업하지 않는 것으로 본다
	 */
	static boolean coversStay(List<Period> periods, Weekday weekday, LocalTime arrival, int stayMinutes) {
		if (periods == null) {
			return false;
		}
		int googleDay = (weekday.ordinal() + 1) % 7;
		int arrivalMinute = minuteOfWeek(googleDay, arrival.getHour(), arrival.getMinute());
		return periods.stream().anyMatch(period -> period != null && period.coversStay(arrivalMinute, stayMinutes));
	}

	private static int minuteOfWeek(Point point) {
		return minuteOfWeek(point.day(), point.hour(), point.minute());
	}

	private static int minuteOfWeek(int day, int hour, int minute) {
		return day * MINUTES_PER_DAY + hour * 60 + minute;
	}
}
