package com.odyssey.cellslotscore.repository;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.*;

/**
 * 시간대 점수 조회를 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>엔티티가 읽기 전용이라 시드는 SQL 로 넣는다.
 * 테스트마다 트랜잭션을 되돌려 서로 영향을 주지 않는다.
 * 다른 달 · 요일 · 시간대와 서로 다른 peakHour/붐빔 등급을 함께 넣어 조건 누락을 검출한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellSlotScoreRepositoryTest {
	@Autowired private CellSlotScoreRepository repository;
	@Autowired private JdbcClient jdbc;

	@BeforeEach
	void seed() {
		jdbc.sql("insert into legal_dong values ('인사동', 'INSADONG'), ('종로', 'JONGNO')").update();
		jdbc.sql("""
			insert into cell values
			(1, '인사동', 37, 126, 38, 127),
			(2, '인사동', 37, 126, 38, 127),
			(3, '종로', 37, 126, 38, 127)
			""").update();
		jdbc.sql("""
			insert into cell_slot_score values
			(3, 11, 'MON_THU', 'AFTERNOON', 'LESS_BUSY', false, 90),
			(1, 11, 'MON_THU', 'AFTERNOON', 'LESS_BUSY', true, 40),
			(2, 11, 'MON_THU', 'AFTERNOON', 'LIVELY', false, 90),
			(1, 10, 'MON_THU', 'AFTERNOON', 'LIVELY', false, 100),
			(2, 11, 'FRI', 'AFTERNOON', 'LIVELY', true, 100),
			(3, 11, 'MON_THU', 'DINNER', 'LIVELY', true, 100)
			""").update();
	}

	@Test
	@DisplayName("해당 달·요일·시간대의 칸을 점수 내림차순, 동점은 칸 번호순으로 준다")
	void orderAndPeriod() {
		// given / when
		var rows = repository.findRankedCells(11, DayType.MON_THU, TimeSlot.AFTERNOON, null, null);
		// then
		assertThat(rows).extracting(RankedCell::cellId).containsExactly(2L, 3L, 1L);
	}

	@Test
	@DisplayName("활기참은 붐빔 등급이 아니라 peakHour로 고른다")
	void peakHour() {
		// given / when
		var rows = repository.findRankedCells(11, DayType.MON_THU,
			TimeSlot.AFTERNOON, null, DensityLevel.LIVELY);
		// then
		assertThat(rows).extracting(RankedCell::cellId).containsExactly(1L);
	}

	@Test
	@DisplayName("덜 붐빔과 지역은 법정동 지역과 붐빔 등급으로 거른다")
	void areaAndDensity() {
		// given / when
		var rows = repository.findRankedCells(11, DayType.MON_THU,
			TimeSlot.AFTERNOON, Area.INSADONG, DensityLevel.LESS_BUSY);
		// then
		assertThat(rows).extracting(RankedCell::cellId).containsExactly(1L);
	}
}
