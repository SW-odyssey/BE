package com.odyssey.recommendation.v2;

import java.time.LocalTime;

import com.odyssey.cellcondition.entity.Weekday;

interface CourseHoursLookup extends CourseVisitChecker {

	Hours load(String googlePlaceId);

	@Override
	default Result check(String googlePlaceId, Weekday weekday, LocalTime visitTime, int stayMinutes) {
		return load(googlePlaceId).during(weekday, visitTime, stayMinutes);
	}

	interface Hours {
		Result during(Weekday weekday, LocalTime arrival, int stayMinutes);

		static Hours fixed(Result result) {
			return (weekday, arrival, minutes) -> result;
		}
	}
}
