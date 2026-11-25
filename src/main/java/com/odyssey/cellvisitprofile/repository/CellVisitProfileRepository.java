package com.odyssey.cellvisitprofile.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.cellvisitprofile.VisitProfile;
import com.odyssey.cellvisitprofile.VisitProfile.VisitBar;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;
import com.odyssey.global.api.ApiEnums;

/** 칸·요일 방문 프로필(cell_visit_profile)과 시간대별 막대(cell_visit_bar) 조회. */
@Repository
public class CellVisitProfileRepository {

	/** 프로필 한 줄과 막대 한 줄을 이은 행. 막대가 없는 프로필은 slot 과 height 가 null 이다. */
	private record Row(VisitProfileStatus status, TimeSlot slot, Integer relativeHeight) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public CellVisitProfileRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 같은 분석 배치·요일·칸의 프로필 하나를 조회한다. 다른 칸이나 요일의 프로필은 섞이지 않는다.
	 *
	 * <p>막대는 점심 → 오후 → 저녁 → 심야 순서로 돌려준다. 이 순서의 기준은 이 SQL 한 곳뿐이다.
	 *
	 * @return 프로필이 없으면 비어 있다. 막대가 없는 상태의 프로필은 빈 막대로 돌려준다
	 */
	public Optional<VisitProfile> find(String datasetId, Weekday weekday, long cellId) {
		List<Row> rows = jdbc.query("""
			select p.status, b.slot, b.relative_height
			from cell_visit_profile p
			left join cell_visit_bar b
				on b.dataset_id = p.dataset_id and b.cell_id = p.cell_id
				and b.weekday = p.weekday
			where p.dataset_id = :datasetId and p.weekday = :weekday and p.cell_id = :cellId
			order by case b.slot
				when 'lunch' then 1 when 'afternoon' then 2
				when 'dinner' then 3 when 'late_night' then 4 end
			""", Map.of(
			"datasetId", datasetId,
			"weekday", ApiEnums.toValue(weekday),
			"cellId", cellId),
			(rs, rowNum) -> new Row(
				VisitProfileStatus.valueOf(rs.getString("status")),
				toSlot(rs.getString("slot")),
				rs.getObject("relative_height", Integer.class)));
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		List<VisitBar> bars = rows.stream()
			.filter(row -> row.slot() != null)
			.map(row -> new VisitBar(row.slot(), row.relativeHeight()))
			.toList();
		return Optional.of(new VisitProfile(rows.getFirst().status(), bars));
	}

	/**
	 * 여러 칸의 가장 붐비는 시간대(막대가 100 인 시간대)를 한 번에 조회한다. 같은 배치·요일의 것만 본다.
	 *
	 * @param cellIds 칸 번호들. 비어 있으면 조회하지 않는다
	 * @return 칸 번호 → 가장 붐비는 시간대. 프로필이 없거나 그래프를 그리지 않는 상태의 칸은 빠진다
	 */
	public Map<Long, TimeSlot> findPeakSlots(String datasetId, Weekday weekday, Collection<Long> cellIds) {
		if (cellIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, TimeSlot> peaks = new HashMap<>();
		jdbc.query("""
			select b.cell_id, b.slot
			from cell_visit_profile p
			join cell_visit_bar b
				on b.dataset_id = p.dataset_id and b.cell_id = p.cell_id
				and b.weekday = p.weekday
			where p.dataset_id = :datasetId and p.weekday = :weekday and p.cell_id in (:cellIds)
				and p.status = 'AVAILABLE' and b.relative_height = 100
			""", Map.of(
			"datasetId", datasetId,
			"weekday", ApiEnums.toValue(weekday),
			"cellIds", cellIds),
			rs -> {
				peaks.put(rs.getLong("cell_id"), toSlot(rs.getString("slot")));
			});
		return Map.copyOf(peaks);
	}

	private static TimeSlot toSlot(String value) {
		return value == null ? null : TimeSlot.valueOf(value.toUpperCase(Locale.ROOT));
	}
}
