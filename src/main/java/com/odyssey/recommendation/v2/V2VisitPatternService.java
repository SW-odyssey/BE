package com.odyssey.recommendation.v2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2VisitPatternResponse.*;

@Service
@Transactional(readOnly = true)
public class V2VisitPatternService {
	private final CellInsightRepository repository;
	private final V2DatasetResolver datasets;
	public V2VisitPatternService(CellInsightRepository repository, V2DatasetResolver datasets) {
		this.repository = repository;
		this.datasets = datasets;
	}
	public V2VisitPatternResponse profile(String batchId, String day, long cellId) {
		Dataset batch = datasets.require(batchId);
		day = V2Query.option(day, V2Query.DAYS, "weekday");
		if (cellId <= 0) throw V2Query.invalid("cellId는 양의 정수입니다.");
		ProfileRow row = repository.profile(batchId, day, cellId).orElseThrow(
			() -> new BusinessException(V2Error.CELL_NOT_FOUND, "해당 배치에 셀 자료가 없습니다."));
		Graph graph = null;
		if (row.status().equals("AVAILABLE")) {
			if (row.bars().size() != 4 || row.bars().stream().filter(b -> b.relativeHeight() == 100).count() != 1) {
				throw new IllegalStateException("Invalid published visit profile");
			}
			String peak = row.bars().stream().filter(b -> b.relativeHeight() == 100).findFirst().orElseThrow().slot();
			graph = new Graph("visit", "SAME_CELL_SAME_WEEKDAY", 100, peak,
				row.bars().stream().map(b -> new Bar(b.slot(), b.relativeHeight())).toList());
		}
		return new V2VisitPatternResponse(new PatternContext(batch.id(), batch.referenceMonth(), Long.toString(cellId), day), row.status(), graph);
	}

}
