package com.odyssey.cellcondition.entity;

/** 칸 조건(cell_condition)의 품질 상태. DB 의 CHECK 제약과 같은 네 가지다. */
public enum CellQualityStatus {
	AVAILABLE,          // 추천에 쓸 수 있다. 방문 백분위가 있다
	NOT_OBSERVED,       // 관측되지 않았다
	INCOMPLETE,         // 시간대 자료가 불완전하다
	NO_POSITIVE_VISITS  // 방문이 양수인 기록이 없다
}
