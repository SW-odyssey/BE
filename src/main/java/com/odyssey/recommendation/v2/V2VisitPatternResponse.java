package com.odyssey.recommendation.v2;

import java.util.List;

public record V2VisitPatternResponse(PatternContext context, String status, Graph graph) {
	public record PatternContext(String datasetId, String referenceMonth, String cellId, String weekday) {}
	public record Bar(String slot, int relativeHeight) {}
	public record Graph(String metric, String comparison, int scaleMax, String highlightSlot,
		List<Bar> bars) {}
}
