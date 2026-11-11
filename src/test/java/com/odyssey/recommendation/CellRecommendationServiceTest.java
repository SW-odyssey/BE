package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.odyssey.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 실제 칸 조회부터 응답 경계 변환까지 검증한다. 테스트 데이터는 종료 후 롤백한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellRecommendationServiceTest {

	@Autowired
	private CellRecommendationService service;

	@Autowired
	private JdbcClient jdbc;

	@Test
	@DisplayName("요청한 셀만 중복 없이 ID순으로 조회하고 네 경계를 보존한다")
	void selectedCellBounds() {
		// given
		jdbc.sql("insert into legal_dong values ('인사동', 'INSADONG')").update();
		jdbc.sql("""
			insert into cell values
			(3, '인사동', 37.57, 126.98, 37.58, 126.99),
			(2, '인사동', 37.58, 126.99, 37.59, 127.00),
			(1, '인사동', 37.56, 126.97, 37.57, 126.98)
			""").update();

		// when
		var response = service.findCells(List.of(3L, 1L, 3L, 999L));

		// then
		assertThat(response.cells()).containsExactly(
			new CellRecommendationResponse.CellItem(1L,
				new CellRecommendationResponse.Bounds(37.56, 126.97, 37.57, 126.98)),
			new CellRecommendationResponse.CellItem(3L,
				new CellRecommendationResponse.Bounds(37.57, 126.98, 37.58, 126.99)));
	}

	@Test
	@DisplayName("요청한 셀이 DB에 없으면 빈 목록을 반환한다")
	void missingCells() {
		// given
		List<Long> ids = List.of(999L);

		// when
		var response = service.findCells(ids);

		// then
		assertThat(response.cells()).isEmpty();
	}
}
