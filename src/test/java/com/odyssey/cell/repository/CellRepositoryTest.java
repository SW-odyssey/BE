package com.odyssey.cell.repository;

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
import com.odyssey.cell.entity.Cell;

/**
 * {@link CellRepository} 를 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>메서드 이름으로 만들어지는 쿼리라 이름이 바뀌면 조건이나 정렬이 조용히 달라진다. 그것을 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellRepositoryTest {

	@Autowired
	private CellRepository cellRepository;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void seed() {
		cell(1);
		cell(2);
		cell(3);
	}

	@Test
	@DisplayName("요청한 칸만 칸 번호 오름차순으로 준다")
	void findByIdsInOrder() {
		// given: 순서를 거꾸로, 칸 2 는 빼고 요청한다
		List<Long> cellIds = List.of(3L, 1L);

		// when
		List<Cell> cells = cellRepository.findByCellIdInOrderByCellId(cellIds);

		// then
		assertThat(cells).extracting(Cell::getCellId).containsExactly(1L, 3L);
	}

	private void cell(long cellId) {
		jdbc.sql("insert into legal_dong values ('공평동', 'INSADONG') on conflict do nothing").update();
		jdbc.sql("""
				insert into cell (cell_id, legal_dong, sw_lat, sw_lng, ne_lat, ne_lng)
				values (?, '공평동', 37.5698, 126.9797, 37.5702, 126.9803)
				""")
			.params(cellId)
			.update();
	}
}
