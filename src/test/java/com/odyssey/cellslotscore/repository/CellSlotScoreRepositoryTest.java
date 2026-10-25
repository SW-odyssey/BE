package com.odyssey.cellslotscore.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;

/**
 * {@link CellSlotScoreRepository} 를 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>엔티티가 읽기 전용이라 시드는 SQL 로 넣는다. 테스트마다 트랜잭션을 되돌려 서로 영향을 주지 않는다.
 *
 * <p>시드 (평일 · 오후)
 * <pre>
 * 칸  지역      순위  추천 대상  붐빔
 * 1   인사동     2     O         덜 붐빔
 * 2   인사동     1     O         붐빔
 * 3   종로       1     O         덜 붐빔
 * 4   종로       없음  O         없음
 * 5   인사동     3     X         없음
 * </pre>
 * 칸 1 은 주말 · 오후에도 점수가 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellSlotScoreRepositoryTest {

	@Autowired
	private CellSlotScoreRepository cellSlotScoreRepository;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void seed() {
		cell(1, "INSADONG");
		cell(2, "INSADONG");
		cell(3, "JONGNO");
		cell(4, "JONGNO");
		cell(5, "INSADONG");
		score(1, "WEEKDAY", "AFTERNOON", 2, true, "LESS_BUSY");
		score(2, "WEEKDAY", "AFTERNOON", 1, true, "LIVELY");
		score(3, "WEEKDAY", "AFTERNOON", 1, true, "LESS_BUSY");
		score(4, "WEEKDAY", "AFTERNOON", null, true, null);
		score(5, "WEEKDAY", "AFTERNOON", 3, false, null);
		score(1, "WEEKEND", "AFTERNOON", 1, true, "LESS_BUSY");
	}

	@Test
	@DisplayName("순위 오름차순이고, 같은 순위는 칸 번호 순, 순위가 없는 칸은 맨 뒤다")
	void orderedByRank() {
		// given: 평일 · 오후, 지역 · 붐빔 조건 없음

		// when
		List<Long> cellIds = cellSlotScoreRepository.findRankedCellIds(
			DayType.WEEKDAY, TimeSlot.AFTERNOON, null, null);

		// then: 1위 두 칸(2, 3) → 2위(1) → 순위 없음(4). 추천 대상이 아닌 5 는 빠진다
		assertThat(cellIds).containsExactly(2L, 3L, 1L, 4L);
	}

	@Test
	@DisplayName("추천 대상이 아닌 칸은 순위가 있어도 빠진다")
	void excludesNotEligible() {
		// given: 칸 5 는 3위지만 활동량 하위 20% 라 추천 대상이 아니다

		// when
		List<Long> cellIds = cellSlotScoreRepository.findRankedCellIds(
			DayType.WEEKDAY, TimeSlot.AFTERNOON, null, null);

		// then
		assertThat(cellIds).doesNotContain(5L);
	}

	@Test
	@DisplayName("다른 요일 묶음 · 시간대의 점수는 쓰지 않는다")
	void onlyGivenSlot() {
		// given: 주말 · 오후 점수는 칸 1 에만 있다

		// when
		List<Long> cellIds = cellSlotScoreRepository.findRankedCellIds(
			DayType.WEEKEND, TimeSlot.AFTERNOON, null, null);

		// then
		assertThat(cellIds).containsExactly(1L);
	}

	@Test
	@DisplayName("지역을 주면 그 지역 칸만 순위대로 준다")
	void area() {
		// given: 인사동

		// when
		List<Long> cellIds = cellSlotScoreRepository.findRankedCellIds(
			DayType.WEEKDAY, TimeSlot.AFTERNOON, Area.INSADONG, null);

		// then: 종로 칸 3 · 4 가 빠진다
		assertThat(cellIds).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("붐빔 정도를 주면 그 정도의 칸만 순위대로 준다")
	void densityLevel() {
		// given: 덜 붐빔

		// when
		List<Long> cellIds = cellSlotScoreRepository.findRankedCellIds(
			DayType.WEEKDAY, TimeSlot.AFTERNOON, null, DensityLevel.LESS_BUSY);

		// then: 붐빔(2) 과 붐빔 정도가 없는 칸(4)이 빠진다
		assertThat(cellIds).containsExactly(3L, 1L);
	}

	private void cell(long cellId, String area) {
		jdbc.sql("""
				insert into cell (cell_id, area, legal_dong, legal_dong_en,
					center_lat, center_lng, sw_lat, sw_lng, ne_lat, ne_lng)
				values (?, ?, '공평동', 'Gongpyeong-dong', 37.57, 126.98, 37.5698, 126.9797, 37.5702, 126.9803)
				""")
			.params(cellId, area)
			.update();
	}

	private void score(
		long cellId,
		String dayType,
		String timeSlot,
		Integer rank,
		boolean eligible,
		String densityLevel) {

		jdbc.sql("""
				insert into cell_slot_score (cell_id, day_type, time_slot, density_level,
					score, rank, eligible, version, computed_at)
				values (?, ?, ?, ?, 0.5, ?, ?, 'test', now())
				""")
			.params(cellId, dayType, timeSlot, densityLevel, rank, eligible)
			.update();
	}
}
