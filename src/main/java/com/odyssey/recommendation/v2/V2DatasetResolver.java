package com.odyssey.recommendation.v2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

@Service
@Transactional(readOnly = true)
public class V2DatasetResolver {
	private final CellInsightRepository repository;
	public V2DatasetResolver(CellInsightRepository repository) { this.repository = repository; }
	static boolean supported(Dataset result) {
		return result.policyVersion().equals("visit75-band300-v2-split")
			&& result.reasonVersion().equals("visit75-no-worker-v2-split")
			&& result.profileVersion().equals("visit-only-relative100-step5-v2-split");
	}
	public Dataset require(String id) {
		V2Query.dataset(id);
		Dataset result = repository.datasets().stream().filter(d -> d.id().equals(id)).findFirst()
			.orElseThrow(() -> new BusinessException(V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));
		if (!supported(result)) {
			throw new BusinessException(CommonErrorCode.DATA_UNAVAILABLE, "서버가 지원하지 않는 추천 정책입니다.");
		}
		return result;
	}

}
