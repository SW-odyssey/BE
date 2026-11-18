package com.odyssey.recommendation.v2;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * 요청한 분석 배치가 서버가 다룰 수 있는 배치인지 판단한다.
 *
 * <p>추천 엔진 정책 버전 세 개를 아는 곳은 이 클래스뿐이다. 정책이 바뀌면 이 상수만 고친다.
 * 검증 순서는 형식 오류 400, 배치 없음 404, 지원하지 않는 정책 503이다. 400은 요청을 받는 쪽에서
 * 먼저 끝내고, 여기서는 404와 503만 구분한다.
 */
@Service
@Transactional(readOnly = true)
public class V2DatasetResolver {

	static final String POLICY_VERSION = "visit75-band300-v2-split";
	static final String REASON_VERSION = "visit75-no-worker-v2-split";
	static final String PROFILE_VERSION = "visit-only-relative100-step5-v2-split";

	private final RecommendationDatasetRepository repository;

	public V2DatasetResolver(RecommendationDatasetRepository repository) {
		this.repository = repository;
	}

	/**
	 * 서버가 지원하는 정책의 공개 배치를 기준월 내림차순으로 준다.
	 *
	 * @return 지원하는 배치. 없으면 빈 목록
	 */
	public List<Dataset> findSupported() {
		return repository.findPublished().stream().filter(V2DatasetResolver::isSupported).toList();
	}

	/**
	 * 배치 ID로 지원하는 공개 배치를 찾는다. 없으면 다른 달의 배치로 대신하지 않는다.
	 *
	 * @throws BusinessException DATASET_NOT_FOUND(404). 없거나 공개되지 않은 배치
	 * @throws BusinessException DATA_UNAVAILABLE(503). 서버가 지원하지 않는 정책 버전의 배치
	 */
	public Dataset require(String datasetId) {
		Dataset dataset = repository.findPublished().stream()
			.filter(candidate -> candidate.id().equals(datasetId))
			.findFirst()
			.orElseThrow(() -> new BusinessException(
				V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));
		if (!isSupported(dataset)) {
			throw new BusinessException(
				CommonErrorCode.DATA_UNAVAILABLE, "서버가 지원하지 않는 추천 정책입니다.");
		}
		return dataset;
	}

	private static boolean isSupported(Dataset dataset) {
		return POLICY_VERSION.equals(dataset.policyVersion())
			&& REASON_VERSION.equals(dataset.reasonVersion())
			&& PROFILE_VERSION.equals(dataset.profileVersion());
	}
}
