package com.odyssey.recommendation.v2;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellareatype.repository.CellAreaTypeRepository;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.repository.CellVisitProfileRepository;

/**
 * 코스를 다 고른 뒤 고른 칸들의 이유 재료(가장 붐비는 시간대, 동네 시간 유형)를 짧은 트랜잭션 하나에서 읽는다.
 *
 * <p>고를 칸은 Google 영업 확인이 끝나야 정해지므로 {@link CourseCandidates} 와 다른 트랜잭션이다. 공개 배치의
 * 프로필·막대는 적재 후 불변이고 동네 시간 유형은 배치와 무관해서, 두 트랜잭션 사이에 값이 섞이지 않는다.
 */
@Component
class CourseReasonFacts {

	private final CellVisitProfileRepository profiles;
	private final CellAreaTypeRepository areaTypes;

	CourseReasonFacts(CellVisitProfileRepository profiles, CellAreaTypeRepository areaTypes) {
		this.profiles = profiles;
		this.areaTypes = areaTypes;
	}

	/** 칸 수와 무관하게 쿼리 두 번이다. */
	@Transactional(readOnly = true)
	public Facts load(String datasetId, Weekday weekday, Collection<Long> cellIds) {
		return new Facts(profiles.findPeakSlots(datasetId, weekday, cellIds), areaTypes.find(cellIds));
	}

	/**
	 * @param peakSlots 칸 번호 → 그 칸·요일의 가장 붐비는 시간대. 그래프가 없는 칸은 빠진다
	 * @param areaTypes 칸 번호 → 동네 시간 유형. 정해지지 않은 칸은 빠진다
	 */
	record Facts(Map<Long, TimeSlot> peakSlots, Map<Long, String> areaTypes) {

		static final Facts NONE = new Facts(Map.of(), Map.of());

		Facts {
			peakSlots = Map.copyOf(peakSlots);
			areaTypes = Map.copyOf(areaTypes);
		}

		Optional<TimeSlot> peakSlot(long cellId) {
			return Optional.ofNullable(peakSlots.get(cellId));
		}

		Optional<String> areaType(long cellId) {
			return Optional.ofNullable(areaTypes.get(cellId));
		}
	}
}
