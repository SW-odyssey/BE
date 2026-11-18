package com.odyssey.cellinsight.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;

/**
 * V9 의 제약과 공개 트리거를 실제 Postgres 에서 확인한다.
 *
 * <p>운영 DB 에는 V9 가 한 번 적용되면 되돌리기 어렵다. 그래서 "공개된 데이터셋은 항상 형태가 맞다"는 보장이
 * 트리거와 CHECK 에서 실제로 나오는지 마이그레이션 단계에서 고정해 둔다.
 *
 * <p>실패한 SQL 이후에는 트랜잭션이 중단되므로 각 테스트는 예외를 확인한 뒤 바로 끝낸다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class SplitRecommendationSchemaTest {

	private static final String DS = "ds-schema";
	private static final String MON = "mon";
	private static final String PUBLISH_ERROR = "Invalid visit profile bars for publication";

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void seed() {
		db = new SplitRecommendationFixture(jdbc);
		db.cell(101);
		// 공개 전 데이터셋. 자식 행을 넣은 뒤 publish 로 트리거를 태운다
		db.dataset(DS, "2026-10-01", "2026-10-31T00:00:00Z", false);
	}

	private DataAccessException publishFailure() {
		return catchThrowableOfType(DataAccessException.class, () -> db.publish(DS));
	}

	@Test
	@DisplayName("AVAILABLE 프로필에 막대 4개와 높이 100 하나가 있다 → 공개된다")
	void publishValidProfile() {
		// given
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 100);
		db.bar(DS, 101, MON, "afternoon", 50);
		db.bar(DS, 101, MON, "dinner", 30);
		db.bar(DS, 101, MON, "late_night", 0);

		// when
		db.publish(DS);

		// then
		Boolean published = jdbc.sql("select published from recommendation_dataset"
			+ " where dataset_id = ?").params(DS).query(Boolean.class).single();
		assertThat(published).isTrue();
	}

	@Test
	@DisplayName("AVAILABLE 프로필의 막대가 3개뿐이다 → 공개할 수 없다")
	void publishRejectsMissingBar() {
		// given
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 100);
		db.bar(DS, 101, MON, "afternoon", 50);
		db.bar(DS, 101, MON, "dinner", 30);

		// when
		DataAccessException exception = publishFailure();

		// then
		assertThat(exception).hasMessageContaining(PUBLISH_ERROR);
	}

	@Test
	@DisplayName("AVAILABLE 프로필에 높이 100 이 둘이다 → 공개할 수 없다")
	void publishRejectsTwoPeaks() {
		// given
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 100);
		db.bar(DS, 101, MON, "afternoon", 100);
		db.bar(DS, 101, MON, "dinner", 30);
		db.bar(DS, 101, MON, "late_night", 0);

		// when
		DataAccessException exception = publishFailure();

		// then
		assertThat(exception).hasMessageContaining(PUBLISH_ERROR);
	}

	@Test
	@DisplayName("AVAILABLE 프로필에 높이 100 이 없다 → 공개할 수 없다")
	void publishRejectsNoPeak() {
		// given
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 90);
		db.bar(DS, 101, MON, "afternoon", 50);
		db.bar(DS, 101, MON, "dinner", 30);
		db.bar(DS, 101, MON, "late_night", 0);

		// when
		DataAccessException exception = publishFailure();

		// then
		assertThat(exception).hasMessageContaining(PUBLISH_ERROR);
	}

	@Test
	@DisplayName("AVAILABLE 이 아닌 프로필에 막대가 있다 → 공개할 수 없다")
	void publishRejectsBarsOnUnavailableProfile() {
		// given
		db.profile(DS, 101, MON, "NO_POSITIVE_VISITS");
		db.bar(DS, 101, MON, "lunch", 100);

		// when
		DataAccessException exception = publishFailure();

		// then
		assertThat(exception).hasMessageContaining(PUBLISH_ERROR);
	}

	@Test
	@DisplayName("AVAILABLE 조건인데 백분위가 없다 → CHECK 제약이 막는다")
	void availableConditionRequiresPercentile() {
		// when
		DataIntegrityViolationException exception = catchThrowableOfType(
			DataIntegrityViolationException.class,
			() -> db.condition(DS, 101, MON, "lunch", "AVAILABLE", null));

		// then
		assertThat(exception).isNotNull();
	}

	@Test
	@DisplayName("AVAILABLE 이 아닌 조건에 백분위가 있다 → CHECK 제약이 막는다")
	void unavailableConditionForbidsPercentile() {
		// when
		DataIntegrityViolationException exception = catchThrowableOfType(
			DataIntegrityViolationException.class,
			() -> db.condition(DS, 101, MON, "lunch", "NOT_OBSERVED", 80.0));

		// then
		assertThat(exception).isNotNull();
	}

	@Test
	@DisplayName("허용되지 않은 이유 코드 → CHECK 제약이 막는다")
	void reasonCodeMustBeAllowed() {
		// given
		db.condition(DS, 101, MON, "lunch", "AVAILABLE", 90.0);

		// when
		DataIntegrityViolationException exception = catchThrowableOfType(
			DataIntegrityViolationException.class,
			() -> db.reason(DS, 101, MON, "lunch", 1, "WORKER_DISTINCT"));

		// then
		assertThat(exception).isNotNull();
	}

	@Test
	@DisplayName("같은 조건에 이유를 세 번째 위치에 넣는다 → 위치 제약(1~2)이 막는다")
	void reasonPositionLimitedToTwo() {
		// given
		db.condition(DS, 101, MON, "lunch", "AVAILABLE", 90.0);

		// when
		DataIntegrityViolationException exception = catchThrowableOfType(
			DataIntegrityViolationException.class,
			() -> db.reason(DS, 101, MON, "lunch", 3, "TIME_PEAK"));

		// then
		assertThat(exception).isNotNull();
	}
}
