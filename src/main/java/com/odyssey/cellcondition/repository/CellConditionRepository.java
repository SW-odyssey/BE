package com.odyssey.cellcondition.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.odyssey.cellcondition.entity.CellQualityStatus;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.store.repository.StorePlaceRepository;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/**
 * 칸의 조건(cell_condition) 기준 조회. 추천 후보 매장을 찾는다.
 *
 * <p>장소 필드의 열 목록과 매핑은 {@link StorePlaceRepository} 것을 재사용한다.
 * 의존 방향은 cellcondition → store 한쪽뿐이다.
 */
@Repository
public class CellConditionRepository {

	/**
	 * 추천 후보 한 곳. DB 조회 모델이라 응답에 그대로 내보내지 않는다.
	 *
	 * @param place      후보 매장
	 * @param mgmtNo     인허가 관리번호. 같은 점수끼리 순서를 정하는 데 쓴다
	 * @param percentile 칸의 방문 백분위. 선택에만 쓰고 응답에 넣지 않는다
	 */
	public record Candidate(Place place, String mgmtNo, double percentile) {
	}

	/**
	 * 한 칸의 조건과 이유 한 줄. 이유가 없는 칸은 position 과 code 가 null 인 행 하나다.
	 *
	 * @param position 표시 순서. 1 또는 2
	 * @param code     이유 코드
	 */
	public record ReasonRow(long cellId, CellQualityStatus qualityStatus, Integer position,
		String code) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public CellConditionRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 지정한 분석 배치·요일·시간대에서 추천할 수 있는 매장을 조회한다.
	 *
	 * <p>품질이 AVAILABLE 이고 방문 백분위가 하한 이상인 칸의 활성 매장이다. 후보에는 비어 있지 않은
	 * 구글 장소 ID 가 있어야 한다. 하한은 이 SQL 한 곳에서만 적용하고, 값은 호출하는 서비스가 정한다.
	 *
	 * @param datasetId          분석 배치 ID
	 * @param weekday            요일
	 * @param slot               시간대
	 * @param minVisitorPercentile 방문 백분위 하한. 이 값 이상인 칸만 남긴다
	 * @return 후보 매장. 순서는 정해져 있지 않다
	 */
	public List<Candidate> candidates(String datasetId, Weekday weekday, TimeSlot slot,
		double minVisitorPercentile) {
		return jdbc.query("select " + StorePlaceRepository.COLUMNS + """
			, s.mgmt_no, c.visitor_percentile
			from cell_condition c
			join store s on s.cell_id = c.cell_id
			where c.dataset_id = :datasetId and c.weekday = :weekday and c.slot = :slot
				and c.quality_status = 'AVAILABLE'
				and c.visitor_percentile >= :minVisitorPercentile
				and s.active
				and s.google_place_id is not null and trim(s.google_place_id) <> ''
			""", Map.of(
			"datasetId", datasetId,
			"weekday", ApiEnums.toValue(weekday),
			"slot", ApiEnums.toValue(slot),
			"minVisitorPercentile", minVisitorPercentile),
			(rs, rowNum) -> new Candidate(
				StorePlaceRepository.MAPPER.mapRow(rs, rowNum),
				rs.getString("mgmt_no"),
				rs.getDouble("visitor_percentile")));
	}

	/**
	 * 요청한 칸들의 품질 상태와 이유를 한 번에 조회한다.
	 *
	 * <p>이유가 없는 칸도 행이 남도록 LEFT JOIN 한다. 지정한 배치·요일·시간대의 조건이 없는 칸은 행이
	 * 없다. 이유는 같은 배치·칸·요일·시간대의 것만 붙는다.
	 *
	 * @param cellIds 칸 번호들
	 * @return 칸 번호 오름차순, 같은 칸 안에서는 이유의 표시 순서(position)대로
	 */
	public List<ReasonRow> reasons(String datasetId, Weekday weekday, TimeSlot slot,
		List<Long> cellIds) {
		return jdbc.query("""
			select c.cell_id, c.quality_status, r.position, r.code
			from cell_condition c
			left join cell_reason r
				on r.dataset_id = c.dataset_id and r.cell_id = c.cell_id
				and r.weekday = c.weekday and r.slot = c.slot
			where c.dataset_id = :datasetId and c.weekday = :weekday and c.slot = :slot
				and c.cell_id in (:cellIds)
			order by c.cell_id, r.position
			""", Map.of(
			"datasetId", datasetId,
			"weekday", ApiEnums.toValue(weekday),
			"slot", ApiEnums.toValue(slot),
			"cellIds", cellIds),
			(rs, rowNum) -> new ReasonRow(
				rs.getLong("cell_id"),
				CellQualityStatus.valueOf(rs.getString("quality_status")),
				rs.getObject("position", Integer.class),
				rs.getString("code")));
	}
}
