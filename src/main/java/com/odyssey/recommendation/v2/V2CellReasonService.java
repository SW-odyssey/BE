package com.odyssey.recommendation.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.ReasonRow;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2ReasonsResponse.CellReason;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/** 칸별 추천 이유를 만든다. 배치 확인, 조회, 응답 조립만 하고 상태 판정은 CellReason 이 한다. */
@Service
@Transactional(readOnly = true)
public class V2CellReasonService {

	private final CellConditionRepository repository;
	private final V2DatasetResolver datasets;

	public V2CellReasonService(CellConditionRepository repository, V2DatasetResolver datasets) {
		this.repository = repository;
		this.datasets = datasets;
	}

	/**
	 * @return 요청한 cellIds 마다 키와 상태가 있다. 순서도 요청 순서다
	 * @throws BusinessException DATASET_NOT_FOUND(404) 공개되지 않은 배치,
	 *                           DATA_UNAVAILABLE(503) 서버가 지원하지 않는 정책의 배치
	 */
	public V2ReasonsResponse reasons(V2CellReasonsQuery query) {
		Dataset dataset = datasets.require(query.datasetId());
		Map<Long, List<ReasonRow>> rowsByCell = repository
			.reasons(dataset.id(), query.weekday(), query.slot(), query.cellIds()).stream()
			.collect(Collectors.groupingBy(ReasonRow::cellId));
		Map<Long, CellReason> reasonsByCell = new LinkedHashMap<>();
		for (Long cellId : query.cellIds()) {
			reasonsByCell.put(cellId, CellReason.from(rowsByCell.getOrDefault(cellId, List.of())));
		}
		V2Context context = new V2Context(dataset.id(), dataset.referenceMonth(), query.weekday(),
			query.slot());
		return new V2ReasonsResponse(context, reasonsByCell);
	}
}
