package com.odyssey.cellvisitprofile.entity;

/**
 * 칸·요일 방문 프로필의 상태. DB 의 CHECK 제약과 같은 네 가지다.
 *
 * <p>막대 그래프는 AVAILABLE 외의 상태에도 있을 수 있다. 강조할 시간대(100 인 막대 하나)를 정하는 상태는
 * AVAILABLE 하나뿐이다.
 */
public enum VisitProfileStatus {

	AVAILABLE,
	/** 네 시간대 중 품질 검사를 통과하지 못한 시간대가 있다. 그 시간대 막대는 값이 없다. */
	INCOMPLETE_TIME_SLOTS,
	/** 방문이 양수인 시간대가 없다. 막대가 없다. */
	NO_POSITIVE_VISITS,
	/** 1위 시간대가 2위의 1.25배에 못 미쳐 강조할 시간대를 하나로 정하지 않는다. */
	NO_DISTINCT_PEAK
}
