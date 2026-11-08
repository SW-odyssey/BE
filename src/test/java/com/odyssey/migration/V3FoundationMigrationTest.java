package com.odyssey.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V3FoundationMigrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@Test
	@DisplayName("기존 매장을 업그레이드하면 저장 목록 ID와 구 조회용 값이 유지된다")
	void preserveExistingStore() throws Exception {
		// given
		String schema = prepareV6();
		try (Connection connection = connect(schema)) {
			connection.createStatement().execute("""
				insert into cell values
				(1, null, 'INSADONG', '인사동', 'Insa-dong', 37, 127, 36, 126, 38, 128);
				insert into store (id, mgmt_no, cell_id, name, category, food_type,
					franchise, road_addr, lat, lng, license_date, active)
				values (42, 'test-42', 1, '가게', 'FOOD', 'KOREAN',
					false, '주소', 37, 127, '2000-01-01', true);
				""");

			// when
			migrate(schema, "latest");

			// then
			try (var result = connection.createStatement().executeQuery("""
				select s.id, s.kind, s.since_date, s.category, s.license_date, d.area
				from store s join cell c on c.cell_id = s.cell_id
				join legal_dong d on d.name = c.legal_dong
				""")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getLong("id")).isEqualTo(42);
				assertThat(result.getString("kind")).isEqualTo("KOREAN");
				assertThat(result.getObject("since_date")).isNull();
				assertThat(result.getString("category")).isEqualTo("FOOD");
				assertThat(result.getDate("license_date").toLocalDate().toString())
					.isEqualTo("2000-01-01");
				assertThat(result.getString("area")).isEqualTo("INSADONG");
			}
		}
	}

	@Test
	@DisplayName("같은 법정동에 서로 다른 지역이 있으면 마이그레이션을 중단한다")
	void rejectConflictingAreas() throws Exception {
		// given
		String schema = prepareV6();
		try (Connection connection = connect(schema)) {
			connection.createStatement().execute("""
				insert into cell values
				(1, null, 'INSADONG', '인사동', 'Insa-dong', 37, 127, 36, 126, 38, 128),
				(2, null, 'JONGNO', '인사동', 'Insa-dong', 37, 127, 36, 126, 38, 128)
				""");
		}

		// when
		FlywayException error = catchThrowableOfType(
			() -> migrate(schema, "latest"), FlywayException.class);

		// then
		assertThat(error).hasStackTraceContaining("Conflicting areas for legal_dong");
	}

	@Test
	@DisplayName("빈 DB를 생성하면 새 테이블 네 개의 공개 접근이 차단된다")
	void enableRls() throws Exception {
		// given
		String schema = "test_" + UUID.randomUUID().toString().replace("-", "");

		// when
		migrate(schema, "latest");

		// then
		try (Connection connection = connect(schema);
			 var result = connection.createStatement().executeQuery("""
				select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
				where n.nspname = current_schema() and c.relrowsecurity
				and c.relname in ('legal_dong', 'store_badge', 'cell_month_metric', 'score_batch')
				""")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(4);
		}
	}

	private String prepareV6() {
		String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
		migrate(schema, "6");
		return schema;
	}

	private void migrate(String schema, String target) {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.schemas(schema).defaultSchema(schema).target(target)
			.load().migrate();
	}

	private Connection connect(String schema) throws Exception {
		Connection connection = DriverManager.getConnection(
			POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		connection.setSchema(schema);
		return connection;
	}
}
