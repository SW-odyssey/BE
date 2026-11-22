package com.odyssey.cellareatype.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;

/** {@link CellAreaTypeRepository} 와 cell_area_type 제약을 실제 Postgres 에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellAreaTypeRepositoryTest {

	@Autowired
	private CellAreaTypeRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private void areaType(long cellId, String type) {
		jdbc.sql("insert into cell_area_type (cell_id, area_type, source) values (?, ?, 'test')")
			.params(cellId, type).update();
	}

	@Test
	@DisplayName("유형이 있는 칸 둘과 없는 칸 하나 → 있는 칸만 돌려준다")
	void find() {
		// given
		areaType(101, "late_night");
		areaType(102, "weekend_day");
		areaType(103, "mixed");

		// when
		Map<Long, String> types = repository.find(List.of(101L, 102L, 999L));

		// then
		assertThat(types).containsExactlyInAnyOrderEntriesOf(Map.of(101L, "late_night", 102L, "weekend_day"));
	}

	@Test
	@DisplayName("칸 목록이 비었다 → 빈 결과다")
	void findEmpty() {
		// when
		Map<Long, String> types = repository.find(List.of());

		// then
		assertThat(types).isEmpty();
	}

	@Test
	@DisplayName("네 유형 밖의 값을 넣는다 → DB 가 거절한다")
	void rejectsUnknownType() {
		// when & then
		assertThatThrownBy(() -> areaType(101, "worker")).isInstanceOf(DataIntegrityViolationException.class);
	}
}
