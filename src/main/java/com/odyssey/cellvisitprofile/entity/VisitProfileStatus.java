package com.odyssey.cellvisitprofile.entity;

/**
 * 칸·요일 방문 프로필의 상태. DB 의 CHECK 제약과 같은 네 가지다.
 *
 * <p>그래프를 그릴 수 있는 상태는 AVAILABLE 하나뿐이다. 그래프를 그리지 않는 상태는 시간대별 막대가
 * 없다.
 */
public enum VisitProfileStatus {

	AVAILABLE(true),
	/** 시간대 자료가 불완전하다. */
	INCOMPLETE_TIME_SLOTS(false),
	/** 방문이 양수인 시간대가 없다. */
	NO_POSITIVE_VISITS(false),
	/** 가장 높은 시간대가 하나로 정해지지 않는다. */
	NO_DISTINCT_PEAK(false);

	private final boolean showsGraph;

	VisitProfileStatus(boolean showsGraph) {
		this.showsGraph = showsGraph;
	}

	/** @return 이 상태에서 시간대별 막대 그래프를 그리는지 */
	public boolean showsGraph() {
		return showsGraph;
	}
}
