package com.odyssey.cell.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

/**
 * {@link CellRepository} 를 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>지역별 평균 중심 좌표 쿼리가 바뀌어도 메타의 기준 좌표가 조용히 달라지지 않게 막는다.
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
	@DisplayName("칸이 있는 지역마다 칸 중심 좌표의 평균을 준다")
	void findAreaCenters() {
		// given: 명동 칸 둘의 중심은 (37.5635, 126.9825), (37.5655, 126.9845)
		myeongdongCell(4, 37.5630, 126.9820, 37.5640, 126.9830);
		myeongdongCell(5, 37.5650, 126.9840, 37.5660, 126.9850);

		// when
		List<AreaCenter> centers = cellRepository.findAreaCenters();

		// then
		assertThat(centers).extracting(AreaCenter::area)
			.containsExactlyInAnyOrder(Area.INSADONG, Area.MYEONGDONG);
		AreaCenter myeongdong = centers.stream()
			.filter(center -> center.area() == Area.MYEONGDONG).findFirst().orElseThrow();
		assertThat(myeongdong.lat()).isCloseTo(37.5645, within(1e-9));
		assertThat(myeongdong.lng()).isCloseTo(126.9835, within(1e-9));
		AreaCenter insadong = centers.stream()
			.filter(center -> center.area() == Area.INSADONG).findFirst().orElseThrow();
		assertThat(insadong.lat()).isCloseTo(37.5700, within(1e-9));
		assertThat(insadong.lng()).isCloseTo(126.9800, within(1e-9));
	}

	private void myeongdongCell(long cellId, double swLat, double swLng, double neLat, double neLng) {
		jdbc.sql("insert into legal_dong values ('명동2가', 'MYEONGDONG') on conflict do nothing").update();
		jdbc.sql("""
				insert into cell (cell_id, legal_dong, sw_lat, sw_lng, ne_lat, ne_lng)
				values (?, '명동2가', ?, ?, ?, ?)
				""")
			.params(cellId, swLat, swLng, neLat, neLng)
			.update();
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
