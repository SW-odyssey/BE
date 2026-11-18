package com.odyssey.recommendation.v2;

import java.util.List;

/** 첫 화면의 선택지. 프론트는 여기서 받은 값만 골라 다음 요청에 보낸다. */
public record V2MetaResponse(
	List<DatasetOption> datasets,
	List<String> weekdays,
	List<String> slots,
	List<String> categories,
	List<String> kinds) {

	/** 고를 수 있는 분석 배치. id 를 그대로 datasetId 로 보낸다. */
	public record DatasetOption(String id, String referenceMonth, String population) {
	}
}
