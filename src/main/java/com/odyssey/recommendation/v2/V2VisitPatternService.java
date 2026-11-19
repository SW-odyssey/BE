package com.odyssey.recommendation.v2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellvisitprofile.VisitProfile;
import com.odyssey.cellvisitprofile.repository.CellVisitProfileRepository;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/** 방문 패턴을 만든다. 배치 확인, 프로필 조회, 응답 조립만 하고 막대 규칙은 VisitProfile 이 지킨다. */
@Service
@Transactional(readOnly = true)
public class V2VisitPatternService {

	private final CellVisitProfileRepository repository;
	private final V2DatasetResolver datasets;

	public V2VisitPatternService(CellVisitProfileRepository repository,
		V2DatasetResolver datasets) {
		this.repository = repository;
		this.datasets = datasets;
	}

	/**
	 * @throws BusinessException DATASET_NOT_FOUND(404) 공개되지 않은 배치,
	 *                           CELL_NOT_FOUND(404) 이 배치에 이 칸·요일의 프로필이 없음,
	 *                           DATA_UNAVAILABLE(503) 서버가 지원하지 않는 정책의 배치
	 */
	public V2VisitPatternResponse visitPattern(V2VisitPatternQuery query) {
		Dataset dataset = datasets.require(query.datasetId());
		VisitProfile profile = repository
			.find(dataset.id(), query.weekday(), query.cellId())
			.orElseThrow(() -> new BusinessException(
				V2Error.CELL_NOT_FOUND, "해당 배치에 셀 자료가 없습니다."));
		return V2VisitPatternResponse.from(dataset, query.cellId(), query.weekday(), profile);
	}
}
