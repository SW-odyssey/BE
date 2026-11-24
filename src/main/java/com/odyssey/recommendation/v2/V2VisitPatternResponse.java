package com.odyssey.recommendation.v2;

import java.util.List;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.VisitProfile;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * 상세 화면의 요일별 방문 패턴. 막대가 없는 프로필은 {@code graph} 가 null 이고 키는 남는다.
 * 방문 값이 없는 것을 0 인 막대로 바꾸지 않는다.
 *
 * <p>status 는 대문자 이름 그대로 나간다(계약이 AVAILABLE 형태다). weekday 와 시간대는 Jackson
 * 설정으로 소문자가 된다.
 */
public record V2VisitPatternResponse(PatternContext context, String status, Graph graph) {

	static final String METRIC = "visit";
	static final String COMPARISON = "SAME_CELL_SAME_WEEKDAY";
	static final int SCALE_MAX = 100;

	/**
	 * @param referenceMonth 분석 기준 월(YYYY-MM)
	 * @param cellId         요청한 칸 번호. 문자열로 나간다
	 */
	public record PatternContext(String datasetId, String referenceMonth, String cellId,
		Weekday weekday) {
	}

	/** @param relativeHeight 품질 검사를 통과하지 못한 시간대는 null 이다. 0 으로 바꾸지 않는다 */
	public record Bar(TimeSlot slot, Integer relativeHeight) {
	}

	/**
	 * @param highlightSlot 100 인 막대의 시간대. AVAILABLE 이 아니면 null 이다
	 * @param bars          점심 → 심야 순서. 합이 100 일 필요는 없다
	 */
	public record Graph(String metric, String comparison, int scaleMax, TimeSlot highlightSlot,
		List<Bar> bars) {
	}

	static V2VisitPatternResponse from(Dataset dataset, long cellId, Weekday weekday,
		VisitProfile profile) {
		Graph graph = profile.bars().isEmpty() ? null
			: new Graph(METRIC, COMPARISON, SCALE_MAX, profile.peakSlot().orElse(null), profile.bars().stream()
				.map(bar -> new Bar(bar.slot(), bar.relativeHeight()))
				.toList());
		return new V2VisitPatternResponse(
			new PatternContext(dataset.id(), dataset.referenceMonth(), Long.toString(cellId),
				weekday),
			profile.status().name(),
			graph);
	}
}
