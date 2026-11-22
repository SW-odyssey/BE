package com.odyssey.cellareatype.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 칸의 동네 시간 유형(cell_area_type) 조회. 분석 배치와 무관한 값이다. */
@Repository
public class CellAreaTypeRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public CellAreaTypeRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param cellIds 칸 번호들. 비어 있으면 조회하지 않는다
	 * @return 칸 번호 → 동네 시간 유형(late_night, weekend_day, weekday_day, mixed). 유형이 없는 칸은 빠진다
	 */
	public Map<Long, String> find(Collection<Long> cellIds) {
		if (cellIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> types = new HashMap<>();
		jdbc.query("""
			select cell_id, area_type
			from cell_area_type
			where cell_id in (:cellIds)
			""", Map.of("cellIds", cellIds),
			rs -> {
				types.put(rs.getLong("cell_id"), rs.getString("area_type"));
			});
		return Map.copyOf(types);
	}
}
