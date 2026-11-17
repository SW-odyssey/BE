package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;
import com.odyssey.recommendation.v2.V2ReasonsResponse.*;

@Service
@Transactional(readOnly = true)
public class V2CellReasonService {
	private final CellInsightRepository repository;
	private final V2DatasetResolver datasets;
	public V2CellReasonService(CellInsightRepository repository, V2DatasetResolver datasets) {
		this.repository = repository;
		this.datasets = datasets;
	}
	public V2ReasonsResponse reasons(String batchId, String day, String slot, List<Long> cells) {
		Dataset batch = datasets.require(batchId);
		day = V2Query.option(day, V2Query.DAYS, "weekday");
		slot = V2Query.option(slot, V2Query.SLOTS, "slot");
		if (cells.isEmpty() || cells.size() > 30 || cells.stream().anyMatch(id -> id == null || id <= 0)
			|| cells.stream().distinct().count() != cells.size()) {
			throw V2Query.invalid("cellIds는 중복 없는 양의 정수 1~30개입니다.");
		}
		Map<String, List<ReasonRow>> grouped = repository.reasons(batchId, day, slot, cells).stream()
			.collect(Collectors.groupingBy(ReasonRow::cellId));
		Map<String, CellReason> result = new LinkedHashMap<>();
		for (Long id : cells) {
			List<ReasonRow> rows = grouped.getOrDefault(id.toString(), List.of());
			if (rows.isEmpty()) {
				result.put(id.toString(), new CellReason("CELL_NOT_IN_DATASET", List.of()));
				continue;
			}
			List<Reason> reasons = rows.stream().filter(r -> r.position() != null && !"WORKER_DISTINCT".equals(r.code()))
				.map(r -> new Reason(r.code())).toList();
			String status = rows.getFirst().qualityStatus();
			if (status.equals("AVAILABLE") && reasons.isEmpty()) status = "NO_RECOMMENDATION_EVIDENCE";
			result.put(id.toString(), new CellReason(status, reasons));
		}
		return new V2ReasonsResponse(new V2Context(batch.id(), batch.referenceMonth(), day, slot), result);
	}

}
