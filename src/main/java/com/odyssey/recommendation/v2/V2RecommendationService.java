package com.odyssey.recommendation.v2;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/** 추천 목록을 만든다. 배치 확인, 후보 조회, 선별, 응답 조립만 맡고 규칙은 selector 가 가진다. */
@Service
@Transactional(readOnly = true)
public class V2RecommendationService {

	private final CellConditionRepository candidates;
	private final V2DatasetResolver datasets;

	public V2RecommendationService(CellConditionRepository candidates, V2DatasetResolver datasets) {
		this.candidates = candidates;
		this.datasets = datasets;
	}

	/**
	 * @return 조건에 맞는 매장이 없어도 오류가 아니라 NO_MATCHES 와 빈 목록이다
	 * @throws BusinessException DATASET_NOT_FOUND(404) 공개되지 않은 배치,
	 *                           DATA_UNAVAILABLE(503) 서버가 지원하지 않는 정책의 배치
	 */
	public V2RecommendationResponse recommend(V2RecommendationQuery query) {
		Dataset dataset = datasets.require(query.datasetId());
		List<Candidate> found = candidates.candidates(dataset.id(), query.weekday(), query.slot(),
			V2RecommendationPolicy.DEFAULT_MIN_VISITOR_PERCENTILE);
		V2Context context = new V2Context(dataset.id(), dataset.referenceMonth(), query.weekday(),
			query.slot());
		List<Selected> selected = V2RecommendationSelector.select(found, query);
		return V2RecommendationResponse.of(context, selected);
	}
}
