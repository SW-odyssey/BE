package com.odyssey.recommendation.v2;

import java.time.LocalTime;
import java.util.List;

import com.odyssey.cellcondition.entity.Weekday;

/**
 * 주 단위 정규 영업시간으로 도착부터 머무는 동안 계속 영업하는지 계산한다. 외부 호출을 모르는 순수한 계산이다.
 *
 * <p>요일은 Google 과 같이 0(일)~6(토)으로 받는다. 자정이나 토→일을 넘는 구간도 있다.
 */
final class WeeklyOpeningHours {

	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	/** @param close 없으면 24시간 영업이다 */
	record Period(Point open, Point close) {
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
		int at = minuteOfWeek((weekday.ordinal() + 1) % 7, arrival.getHour(), arrival.getMinute());
		for (Period period : periods) {
			if (period == null || period.open() == null) {
				continue;
			}
			if (period.close() == null) {
				return true;
			}
			int open = minuteOfWeek(period.open());
			int close = minuteOfWeek(period.close());
			if (close <= open) {
				close += MINUTES_PER_WEEK;
			}
			for (int arrive : new int[] {at, at + MINUTES_PER_WEEK}) {
				if (open <= arrive && arrive < close && arrive + stayMinutes <= close) {
					return true;
				}
			}
		}
		return false;
	}

	private static int minuteOfWeek(Point point) {
		return minuteOfWeek(point.day(), point.hour(), point.minute());
	}

	private static int minuteOfWeek(int day, int hour, int minute) {
		return day * MINUTES_PER_DAY + hour * 60 + minute;
	}
}
