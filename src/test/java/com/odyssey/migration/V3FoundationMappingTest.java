package com.odyssey.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cellmonthmetric.entity.CellMonthMetric;
import com.odyssey.cellmonthmetric.entity.CellMonthMetricId;
import com.odyssey.store.entity.StoreBadge;
import com.odyssey.store.entity.StoreBadgeEntry;
import com.odyssey.store.entity.StoreBadgeId;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class V3FoundationMappingTest {

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("같은 칸의 다른 달 지표를 복합 키로 구분해 읽는다")
	void readMonthlyMetric() {
		// given
		seedCell();
		jdbc.sql("""
			insert into cell_month_metric values
			(9001, 10, 'lateNightShare', 0.2), (9001, 11, 'lateNightShare', 0.8)
			""").update();

		// when
		CellMonthMetric metric = entityManager.find(CellMonthMetric.class,
			new CellMonthMetricId(9001L, 11, "lateNightShare"));

		// then
		assertThat(metric.getValue()).isEqualTo(0.8);
		assertThat(metric.getCell().getCellId()).isEqualTo(9001L);
	}

	@Test
	@DisplayName("가게 하나의 여러 배지를 각각 읽을 수 있다")
	void readStoreBadges() {
		// given
		seedCell();
		jdbc.sql("""
			insert into store (id, mgmt_no, cell_id, name, category, franchise,
				road_addr, lat, lng, license_date, active)
			values (9001, 'badge-test', 9001, '가게', 'CAFE', false,
				'주소', 37, 127, '2000-01-01', true)
			""").update();
		jdbc.sql("""
			insert into store_badge values (9001, 'CENTURY_STORE'), (9001, 'ORAE_GAGE')
			""").update();

		// when
		StoreBadgeEntry century = entityManager.find(StoreBadgeEntry.class,
			new StoreBadgeId(9001L, StoreBadge.CENTURY_STORE));
		StoreBadgeEntry orae = entityManager.find(StoreBadgeEntry.class,
			new StoreBadgeId(9001L, StoreBadge.ORAE_GAGE));

		// then
		assertThat(century).isNotSameAs(orae);
		assertThat(century.getId().getBadge()).isEqualTo(StoreBadge.CENTURY_STORE);
		assertThat(orae.getId().getBadge()).isEqualTo(StoreBadge.ORAE_GAGE);
		assertThat(century.getStore().getId()).isEqualTo(9001L);
		assertThat(orae.getStore().getId()).isEqualTo(9001L);
	}

	private void seedCell() {
		jdbc.sql("""
			insert into cell values
			(9001, null, 'INSADONG', '인사동', 'Insa-dong', 37, 127, 36, 126, 38, 128)
			""").update();
	}
}
