package com.odyssey.recommendationdataset.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 분석 배치(recommendation_dataset) 조회.
 *
 * <p>이 테이블의 스키마가 바뀔 때 함께 바뀌는 코드만 둔다. 배치의 정책 버전이 서버가 지원하는 값인지는
 * 판단하지 않는다.
 */
@Repository
public class RecommendationDatasetRepository {

	/** 대상 집단은 V10 에서 열이 빠져 항상 이 값이다. */
	static final String POPULATION = "DOMESTIC_20_39";

	/** 공개된 배치 한 건. referenceMonth 는 YYYY-MM 형식이다. */
	public record Dataset(String id, String referenceMonth, String population,
		String policyVersion, String reasonVersion, String profileVersion) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public RecommendationDatasetRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 공개된 배치를 기준월 내림차순으로 조회한다. 같은 달이면 최근에 계산한 것, 그래도 같으면 ID 순이다.
	 *
	 * @return 공개된 배치. 없으면 빈 목록
	 */
	public List<Dataset> findPublished() {
		return jdbc.query("""
			select dataset_id, to_char(reference_month, 'YYYY-MM') as month,
				policy_version, reason_version, profile_version
			from recommendation_dataset
			where published
			order by reference_month desc, computed_at desc, dataset_id
			""", Map.of(), (rs, rowNum) -> new Dataset(
			rs.getString("dataset_id"),
			rs.getString("month"),
			POPULATION,
			rs.getString("policy_version"),
			rs.getString("reason_version"),
			rs.getString("profile_version")));
	}
}
