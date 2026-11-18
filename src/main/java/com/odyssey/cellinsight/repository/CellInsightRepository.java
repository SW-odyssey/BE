package com.odyssey.cellinsight.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 공개 응답과 분리된 DB 조회 모델. 방문 백분위는 매장 선택에만 쓰고 응답에 노출하지 않는다. */
@Repository
public class CellInsightRepository {
	private final NamedParameterJdbcTemplate jdbc;

	public CellInsightRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

	public record Dataset(String id, String referenceMonth, String population,
		String policyVersion, String reasonVersion, String profileVersion) {}
	public record Place(long id, String name, String cellId, String kind, double lat, double lng,
		LocalDate sinceDate, String googlePlaceId) {}
	public record Candidate(Place place, String mgmtNo, double percentile) {}
	public record ReasonRow(String cellId, String qualityStatus, Integer position, String code) {}
	public record VisitBar(String slot, int relativeHeight) {}
	public record ProfileRow(String status, List<VisitBar> bars) {}

	public List<Dataset> datasets() {
		return jdbc.query("""
			select dataset_id, to_char(reference_month,'YYYY-MM') as month, 'DOMESTIC_20_39' as population,
			policy_version, reason_version, profile_version from recommendation_dataset
			where published order by reference_month desc, computed_at desc, dataset_id
			""", Map.of(), (rs, n) -> new Dataset(rs.getString("dataset_id"), rs.getString("month"),
				rs.getString("population"), rs.getString("policy_version"),
				rs.getString("reason_version"), rs.getString("profile_version")));
	}

	public List<Candidate> candidates(String dataset, String day, String slot,
									  double minVisitorPercentile) {
		return jdbc.query("""
			select s.id,s.name,s.cell_id,s.kind,s.lat,s.lng,s.since_date,s.google_place_id,
			s.mgmt_no,c.visitor_percentile from cell_condition c
			join store s on s.cell_id=c.cell_id
			where c.dataset_id=:dataset and c.weekday=:day and c.slot=:slot
			and c.quality_status='AVAILABLE' and c.visitor_percentile>=:minVisitorPercentile
			and s.active and s.google_place_id is not null and trim(s.google_place_id)<>''
			""", Map.of("dataset", dataset, "day", day, "slot", slot,
			"minVisitorPercentile", minVisitorPercentile), (rs, n) -> new Candidate(
				place(rs), rs.getString("mgmt_no"), rs.getDouble("visitor_percentile")));
	}

	/** 셀 30개도 한 번의 JOIN으로 조회한다. LEFT JOIN은 이유 없는 셀의 상태를 보존한다. */
	public List<ReasonRow> reasons(String dataset, String day, String slot, List<Long> ids) {
		return jdbc.query("""
			select c.cell_id,c.quality_status,r.position,r.code from cell_condition c
			left join cell_reason r on r.dataset_id=c.dataset_id and r.cell_id=c.cell_id
			and r.weekday=c.weekday and r.slot=c.slot and r.code<>'WORKER_DISTINCT'
			where c.dataset_id=:dataset and c.weekday=:day and c.slot=:slot
			and c.cell_id in (:ids) order by c.cell_id,r.position
			""", Map.of("dataset", dataset, "day", day, "slot", slot, "ids", ids),
			(rs, n) -> new ReasonRow(rs.getString("cell_id"), rs.getString("quality_status"),
				rs.getObject("position", Integer.class), rs.getString("code")));
	}

	public Optional<ProfileRow> profile(String dataset, String day, long cellId) {
		var params = Map.of("dataset", dataset, "day", day, "cell", cellId);
		var statuses = jdbc.query("""
			select status from cell_visit_profile
			where dataset_id=:dataset and weekday=:day and cell_id=:cell
			""", params, (rs, n) -> rs.getString(1));
		if (statuses.isEmpty()) return Optional.empty();
		var bars = jdbc.query("""
			select slot,relative_height from cell_visit_bar
			where dataset_id=:dataset and weekday=:day and cell_id=:cell
			order by case slot when 'lunch' then 1 when 'afternoon' then 2
			when 'dinner' then 3 else 4 end
			""", params, (rs, n) -> new VisitBar(rs.getString(1), rs.getInt(2)));
		return Optional.of(new ProfileRow(statuses.getFirst(), bars));
	}

	public Optional<Place> place(long id) {
		return jdbc.query("select id,name,cell_id,kind,lat,lng,since_date,google_place_id from store where id=:id and active", Map.of("id", id),
			(rs, n) -> place(rs)).stream().findFirst();
	}

	private static Place place(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Place(rs.getLong("id"), rs.getString("name"), rs.getString("cell_id"),
			rs.getString("kind").toLowerCase(java.util.Locale.ROOT), rs.getDouble("lat"),
			rs.getDouble("lng"), rs.getObject("since_date", LocalDate.class), rs.getString("google_place_id"));
	}
}
