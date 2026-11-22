package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellareatype.repository.CellAreaTypeRepository;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.repository.CellVisitProfileRepository;
import com.odyssey.recommendation.v2.CourseReasonFacts.Facts;

class CourseReasonFactsTest {
	@Test
	@DisplayName("칸마다 조회된 재료가 다르다 → 두 재료를 칸별로 독립적으로 돌려준다")
	void loadsIndependentFactsByCell() {
		// given
		CellVisitProfileRepository profiles = mock(CellVisitProfileRepository.class);
		CellAreaTypeRepository areaTypes = mock(CellAreaTypeRepository.class);
		List<Long> cellIds = List.of(101L, 102L, 103L);
		given(profiles.findPeakSlots("nov", Weekday.FRI, cellIds)).willReturn(Map.of(101L, TimeSlot.DINNER));
		given(areaTypes.find(cellIds)).willReturn(Map.of(102L, "mixed"));

		// when
		Facts facts = new CourseReasonFacts(profiles, areaTypes).load("nov", Weekday.FRI, cellIds);

		// then
		assertThat(facts.peakSlot(101L)).contains(TimeSlot.DINNER);
		assertThat(facts.areaType(101L)).isEmpty();
		assertThat(facts.peakSlot(102L)).isEmpty();
		assertThat(facts.areaType(102L)).contains("mixed");
		assertThat(facts.peakSlot(103L)).isEmpty();
		assertThat(facts.areaType(103L)).isEmpty();
	}

	@Test
	@DisplayName("재료를 만든 뒤 원본 지도를 바꾼다 → 이미 조회한 칸의 재료는 그대로다")
	void snapshotsSourceMaps() {
		// given
		Map<Long, TimeSlot> peaks = new HashMap<>(Map.of(101L, TimeSlot.DINNER));
		Map<Long, String> areas = new HashMap<>(Map.of(101L, "mixed"));
		Facts facts = new Facts(peaks, areas);

		// when
		peaks.put(101L, TimeSlot.LUNCH);
		areas.remove(101L);

		// then
		assertThat(facts.peakSlot(101L)).contains(TimeSlot.DINNER);
		assertThat(facts.areaType(101L)).contains("mixed");
	}
}
