package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Map;

/** 셀 ID로 직접 조회한다. 누락·기준 미달도 키와 상태를 반환한다. */
public record V2ReasonsResponse(V2Context context, Map<String, CellReason> reasonsByCellId) {
	public record Reason(String code) {}
	public record CellReason(String status, List<Reason> reasons) {}
}
