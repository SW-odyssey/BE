package com.odyssey.recommendation.v2;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * 코스에 필요한 시간대들의 후보를 한 트랜잭션에서 읽는다. 같은 배치를 일관되게 읽는 것은 이 트랜잭션이 보장한다.
 *
 * <p>Google 영업 확인은 이 트랜잭션이 끝난 뒤에 한다. 확인 중에 DB 커넥션을 잡고 있지 않기 위해서다.
 */
@Component
class CourseCandidates {

	private final CellConditionRepository candidates;
	private final V2DatasetResolver datasets;

	CourseCandidates(CellConditionRepository candidates, V2DatasetResolver datasets) {
		this.candidates = candidates;
		this.datasets = datasets;
	}

	/**
	 * @param slots 후보가 필요한 시간대. 심야 시작에 심야를 선택하지 않으면 비어 있다
	 * @throws BusinessException DATASET_NOT_FOUND(404) 공개되지 않은 배치,
	 *                           DATA_UNAVAILABLE(503) 서버가 지원하지 않는 정책의 배치
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public CandidatePool load(String datasetId, Weekday weekday, TimeSlot startSlot, List<TimeSlot> slots) {
		Dataset dataset = datasets.require(datasetId);
		Map<TimeSlot, List<Candidate>> bySlot = loadBySlot(dataset.id(), weekday, slots);
		V2Context context = new V2Context(dataset.id(), dataset.referenceMonth(), weekday, startSlot);
		return new CandidatePool(context, bySlot);
	}

	private Map<TimeSlot, List<Candidate>> loadBySlot(String datasetId, Weekday weekday, List<TimeSlot> slots) {
		Map<TimeSlot, List<Candidate>> bySlot = new EnumMap<>(TimeSlot.class);
		for (TimeSlot slot : slots) {
			List<Candidate> found = candidates.candidates(datasetId, weekday, slot,
				CourseSchedule.MIN_COURSE_PERCENTILE);
			bySlot.put(slot, found);
		}
		return bySlot;
	}
}
