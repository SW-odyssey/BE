package com.odyssey.recommendation.v2;

import java.time.LocalTime;

import com.odyssey.cellcondition.entity.Weekday;

/** 코스에 넣을 매장이 도착부터 머무는 동안 계속 영업하는지 응답 시점에 확인한다. 결과는 저장하지 않는다. */
interface CourseVisitChecker {

	/** @param stayMinutes 도착 뒤 머무는 시간(분). 마감 정각에 나오는 것은 허용한다 */
	Result check(String googlePlaceId, Weekday weekday, LocalTime visitTime, int stayMinutes);

	enum Result {
		/** 영업 중으로 등록돼 있고 도착부터 머무는 동안 정규 영업시간 안이다. */
		OPEN,
		/** 폐업·휴업이거나 도착 시각이 영업시간 밖이거나 머무는 중에 문을 닫는다. */
		NOT_OPEN,
		/** 영업 중으로 등록돼 있지만 정규 영업시간이 없다. 코스가 종류에 따라 받을지 정한다. */
		HOURS_UNKNOWN,
		/** 외부 조회가 실패했다. 매장 탓이 아니므로 다음 후보로 넘어가지 않고 구간을 멈춘다. */
		UNAVAILABLE
	}
}
