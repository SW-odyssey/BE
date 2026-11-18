package com.odyssey.recommendationdataset.repository;

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
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;
import com.odyssey.support.SplitRecommendationFixture;

/** {@link RecommendationDatasetRepository} 의 공개 필터와 정렬을 실제 Postgres 에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RecommendationDatasetRepositoryTest {

	@Autowired
	private RecommendationDatasetRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void setUp() {
		db = new SplitRecommendationFixture(jdbc);
	}

	@Test
	@DisplayName("공개된 배치가 없다 → 빈 목록을 준다")
	void findPublishedEmpty() {
		// given: 공개 전 배치만 있다
		db.dataset("ds-draft", "2026-10-01", "2026-10-31T00:00:00Z", false);

		// when
		List<Dataset> datasets = repository.findPublished();

		// then
		assertThat(datasets).isEmpty();
	}

	@Test
	@DisplayName("공개 전 배치가 섞여 있다 → 공개된 것만 준다")
	void findPublishedOnlyPublished() {
		// given
		db.dataset("ds-public", "2026-10-01", "2026-10-31T00:00:00Z", true);
		db.dataset("ds-draft", "2026-11-01", "2026-11-02T00:00:00Z", false);

		// when
		List<Dataset> datasets = repository.findPublished();

		// then
		assertThat(datasets).extracting(Dataset::id).containsExactly("ds-public");
	}

	@Test
	@DisplayName("기준월과 계산 시각이 제각각이다 → 최신 기준월, 같은 달이면 최근 계산, 그래도 같으면 ID 순이다")
	void findPublishedOrder() {
		// given: 일부러 정렬 순서와 다르게 넣는다
		db.dataset("ds-sep", "2026-09-01", "2026-12-01T00:00:00Z", true);
		db.dataset("ds-oct-early", "2026-10-01", "2026-10-31T08:00:00Z", true);
		db.dataset("ds-b-oct-late", "2026-10-01", "2026-11-01T00:00:00Z", true);
		db.dataset("ds-a-oct-late", "2026-10-01", "2026-11-01T00:00:00Z", true);

		// when
		List<Dataset> datasets = repository.findPublished();

		// then
		assertThat(datasets).extracting(Dataset::id)
			.containsExactly("ds-a-oct-late", "ds-b-oct-late", "ds-oct-early", "ds-sep");
	}

	@Test
	@DisplayName("배치 한 건 → 기준월은 YYYY-MM, 대상은 고정값, 버전 셋은 저장값 그대로 준다")
	void findPublishedFields() {
		// given
		db.dataset("ds-2026-10", "2026-10-01", "2026-10-31T00:00:00Z", true);

		// when
		List<Dataset> datasets = repository.findPublished();

		// then
		assertThat(datasets).containsExactly(new Dataset(
			"ds-2026-10", "2026-10", "DOMESTIC_20_39", "policy-1", "reason-1", "profile-1"));
	}
}
