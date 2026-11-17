package com.odyssey.recommendation.v2;

import java.util.List;

public record V2MetaResponse(List<DatasetOption> datasets, List<String> weekdays,
	List<String> slots, List<String> categories, List<String> kinds) {
	public record DatasetOption(String id, String referenceMonth, String population) {}
}
