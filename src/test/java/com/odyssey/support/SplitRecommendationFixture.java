package com.odyssey.support;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * v2 분할 추천 테이블에 시드를 넣는 테스트 도우미.
 *
 * <p>BE 엔티티가 읽기 전용이라 시드는 SQL 로 넣는다. 테스트마다 트랜잭션을 되돌리므로 서로 영향을 주지 않는다.
 */
public final class SplitRecommendationFixture {

	private final JdbcClient jdbc;

	public SplitRecommendationFixture(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void cell(long cellId) {
		jdbc.sql("insert into legal_dong values ('공평동', 'INSADONG') on conflict do nothing").update();
		jdbc.sql("""
			insert into cell (cell_id, legal_dong, sw_lat, sw_lng, ne_lat, ne_lng)
			values (?, '공평동', 37.5698, 126.9797, 37.5702, 126.9803)
			""").params(cellId).update();
	}

	/** month 는 "2026-10-01", computedAt 은 "2026-10-31T08:00:00Z" 형식이다. */
	public void dataset(String datasetId, String month, String computedAt, boolean published) {
		jdbc.sql("""
			insert into recommendation_dataset (dataset_id, reference_month, policy_version,
				reason_version, profile_version, source_sha256, computed_at, published)
			values (?, cast(? as date), 'policy-1', 'reason-1', 'profile-1', ?,
				cast(? as timestamptz), ?)
			""").params(datasetId, month, "0".repeat(64), computedAt, published).update();
	}

	/** 자식 행을 다 넣은 뒤 공개한다. 공개 시점에 DB 트리거가 프로필·막대를 검증한다. */
	public void publish(String datasetId) {
		jdbc.sql("update recommendation_dataset set published = true where dataset_id = ?")
			.params(datasetId).update();
	}

	public void condition(String datasetId, long cellId, String weekday, String slot,
		String qualityStatus, Double percentile) {
		jdbc.sql("""
			insert into cell_condition (dataset_id, cell_id, weekday, slot, quality_status,
				visitor_percentile)
			values (?, ?, ?, ?, ?, ?)
			""").params(datasetId, cellId, weekday, slot, qualityStatus, percentile).update();
	}

	public void reason(String datasetId, long cellId, String weekday, String slot, int position, String code) {
		jdbc.sql("""
			insert into cell_reason (dataset_id, cell_id, weekday, slot, position, code)
			values (?, ?, ?, ?, ?, ?)
			""").params(datasetId, cellId, weekday, slot, position, code).update();
	}

	public void profile(String datasetId, long cellId, String weekday, String status) {
		jdbc.sql("""
			insert into cell_visit_profile (dataset_id, cell_id, weekday, status)
			values (?, ?, ?, ?)
			""").params(datasetId, cellId, weekday, status).update();
	}

	public void bar(String datasetId, long cellId, String weekday, String slot, Integer relativeHeight) {
		jdbc.sql("""
			insert into cell_visit_bar (dataset_id, cell_id, weekday, slot, relative_height)
			values (?, ?, ?, ?, ?)
			""").params(datasetId, cellId, weekday, slot, relativeHeight).update();
	}

	/** sinceDate 는 "1995-05-20" 형식이고 null 이면 개업일 없음이다. placeId 가 null 이면 place ID 없음이다. */
	public void store(long id, long cellId, String name, String kind, String sinceDate,
		String placeId, boolean active) {
		jdbc.sql("""
			insert into store (id, mgmt_no, cell_id, name, kind, lat, lng, since_date,
				google_place_id, active)
			values (?, ?, ?, ?, ?, 37.57, 126.98, cast(? as date), ?, ?)
			""").params(id, "mgmt-" + id, cellId, name, kind, sinceDate, placeId, active).update();
	}
}
