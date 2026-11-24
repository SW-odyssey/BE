package com.odyssey.store.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.odyssey.store.StoreNames;
import com.odyssey.store.entity.StoreKind;

/**
 * 장소(store) 한 건의 기본 정보 조회.
 *
 * <p>추천 후보 조회도 같은 장소 필드를 읽으므로 열 목록({@link #COLUMNS})과 매핑({@link #MAPPER})을
 * 여기서 한 번만 정의하고 재사용한다. store 쪽은 추천 조회를 모른다.
 */
@Repository
public class StorePlaceRepository {

	/** 장소 조회가 읽는 열. store 테이블의 별칭은 {@code s} 여야 한다. */
	public static final String COLUMNS =
		"s.id, s.name, s.cell_id, s.kind, s.lat, s.lng, s.since_date, s.google_place_id, "
			+ "t.name_en, t.name_zh, t.name_ja";

	public static final String TRANSLATION_JOIN =
		" left join store_name_translation t on t.store_id = s.id and t.source_name = s.name ";

	/** {@link #COLUMNS} 로 읽은 행을 {@link Place} 로 바꾼다. */
	public static final RowMapper<Place> MAPPER = StorePlaceRepository::mapPlace;

	/**
	 * DB 조회 모델. API 응답이 아니므로 응답 필드가 되는 것은 별도 응답 타입이 정한다.
	 *
	 * @param id            장소 번호(store.id)
	 * @param cellId        장소가 속한 50m 칸 번호
	 * @param kind          analysis 가 정한 단일 가게 분류
	 * @param lat           위도(WGS84)
	 * @param lng           경도(WGS84)
	 * @param sinceDate     이력 기준 개업일. 확인할 수 없으면 null
	 * @param googlePlaceId 구글 장소 ID. 적재 중이거나 비활성 매장이면 null
	 */
	public record Place(long id, String name, long cellId, StoreKind kind, double lat,
		double lng, LocalDate sinceDate, String googlePlaceId, StoreNames names) {

		public Place(long id, String name, long cellId, StoreKind kind, double lat, double lng,
			LocalDate sinceDate, String googlePlaceId) {
			this(id, name, cellId, kind, lat, lng, sinceDate, googlePlaceId, StoreNames.EMPTY);
		}
	}

	private final NamedParameterJdbcTemplate jdbc;

	public StorePlaceRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 활성 매장 한 곳을 번호로 조회한다. 추천 순위나 Google Place ID 유무와 무관하다.
	 *
	 * @return 활성 매장. 없는 번호이거나 비활성이면 empty
	 */
	public Optional<Place> findActiveById(long id) {
		return jdbc.query(
			"select " + COLUMNS + " from store s" + TRANSLATION_JOIN + "where s.id = :id and s.active",
			Map.of("id", id), MAPPER).stream().findFirst();
	}

	private static Place mapPlace(ResultSet rs, int rowNum) throws SQLException {
		return new Place(
			rs.getLong("id"),
			rs.getString("name"),
			rs.getLong("cell_id"),
			StoreKind.valueOf(rs.getString("kind")),
			rs.getDouble("lat"),
			rs.getDouble("lng"),
			rs.getObject("since_date", LocalDate.class),
			rs.getString("google_place_id"),
			new StoreNames(rs.getString("name_en"), rs.getString("name_zh"), rs.getString("name_ja")));
	}
}
