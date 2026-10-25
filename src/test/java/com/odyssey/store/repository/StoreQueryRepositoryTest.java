package com.odyssey.store.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

/**
 * {@link StoreRepository} 를 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>엔티티가 읽기 전용이라 시드는 SQL 로 넣는다. 테스트마다 트랜잭션을 되돌려 서로 영향을 주지 않는다.
 *
 * <p>시드: 인사동 칸 A 에 영업 중 한식 2곳 · 프랜차이즈 카페 1곳 · 폐업 술집 1곳, 종로 칸 B 에 카페 1곳
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class StoreQueryRepositoryTest {

	private static final long CELL_A = 81383966L;
	private static final long CELL_B = 81399965L;

	@Autowired
	private StoreRepository storeQueryRepository;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void seed() {
		cell(CELL_A, "INSADONG", "공평동");
		cell(CELL_B, "JONGNO", "관철동");
		// id 역순으로 넣는다. Postgres 는 ORDER BY 가 없으면 보통 넣은 순서로 돌려줘서,
		// id 순으로 넣으면 리포지토리의 orderBy(id) 를 지워도 순서 검증이 통과해 버린다
		store(5, CELL_B, "Cafe Onion", "CAFE", null, "2018-01-01", false, true);
		store(4, CELL_A, "닫은 술집", "FOOD", "BAR", "2000-01-01", false, false);
		store(3, CELL_A, "스타벅스 공평점", "CAFE", null, "2015-01-01", true, true);
		store(2, CELL_A, "백부장집", "FOOD", "KOREAN", "1968-03-11", false, true);
		store(1, CELL_A, "공평식당", "FOOD", "KOREAN", "1995-05-20", false, true);
	}

	@Test
	@DisplayName("조건이 없으면 칸 안의 영업 중 · 비프랜차이즈 매장만 칸 정보와 함께 준다")
	void noFilter() {
		// given
		List<Long> cellIds = List.of(CELL_A);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(cellIds, null, null, null, null);

		// then: 프랜차이즈(3)와 폐업(4)은 빠진다
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("공평식당", "백부장집");
		assertThat(rows).extracting(StoreCardRow::area).containsOnly(Area.INSADONG);
	}

	@Test
	@DisplayName("카테고리를 주면 그 카테고리만 남긴다")
	void category() {
		// given
		List<Long> cellIds = List.of(CELL_A, CELL_B);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(
			cellIds, Category.CAFE, null, null, null);

		// then
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("Cafe Onion");
	}

	@Test
	@DisplayName("음식점 종류를 주면 그 종류만 남긴다")
	void foodType() {
		// given
		List<Long> cellIds = List.of(CELL_A, CELL_B);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(
			cellIds, Category.FOOD, FoodType.KOREAN, null, null);

		// then
		assertThat(rows).extracting(StoreCardRow::foodType).containsOnly(FoodType.KOREAN);
		assertThat(rows).hasSize(2);
	}

	@Test
	@DisplayName("노포 기준일을 주면 그 날짜 이전에 개업한 곳만 남긴다")
	void openedBefore() {
		// given: 1990 년 이전 개업
		LocalDate openedBefore = LocalDate.of(1990, 1, 1);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(
			List.of(CELL_A), null, null, openedBefore, null);

		// then
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("백부장집");
	}

	@Test
	@DisplayName("노포 기준일 당일에 개업한 곳도 남긴다")
	void openedBeforeInclusive() {
		// given: 기준일 1990-01-01 에 개업한 매장을 추가
		LocalDate openedBefore = LocalDate.of(1990, 1, 1);
		store(6, CELL_A, "기준일식당", "FOOD", "KOREAN", "1990-01-01", false, true);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(
			List.of(CELL_A), null, null, openedBefore, null);

		// then
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("백부장집", "기준일식당");
	}

	@Test
	@DisplayName("검색어는 대소문자를 무시하고 이름에 포함된 곳을 찾는다")
	void nameContains() {
		// given
		String q = " onion ";

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(
			List.of(CELL_A, CELL_B), null, null, null, q);

		// then
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("Cafe Onion");
	}

	@Test
	@DisplayName("검색어가 공백뿐이면 이름 조건을 걸지 않는다")
	void blankQuery() {
		// given
		String q = "   ";

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(List.of(CELL_A), null, null, null, q);

		// then: 조건이 없을 때와 같다
		assertThat(rows).extracting(StoreCardRow::name).containsExactly("공평식당", "백부장집");
	}

	@Test
	@DisplayName("여러 칸의 매장은 칸과 상관없이 id 순으로 섞여 나온다")
	void multipleCellsById() {
		// given: 칸 B 를 먼저 넘긴다
		List<Long> cellIds = List.of(CELL_B, CELL_A);

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(cellIds, null, null, null, null);

		// then: 칸 순서가 아니라 id 순. 칸 순위 정렬은 서비스가 따로 한다
		assertThat(rows).extracting(StoreCardRow::id).containsExactly(1L, 2L, 5L);
	}

	@Test
	@DisplayName("googlePlaceId 는 있으면 그 값을, 없으면 null 을 준다")
	void googlePlaceId() {
		// given: 공평식당만 구글 장소와 맞춰져 있다
		jdbc.sql("update store set google_place_id = ? where id = ?")
			.params("ChIJ-test-place", 1L)
			.update();

		// when
		List<StoreCardRow> rows = storeQueryRepository.findCards(List.of(CELL_A), null, null, null, null);

		// then: 공평식당(1) → 값, 백부장집(2) → null
		assertThat(rows).extracting(StoreCardRow::googlePlaceId).containsExactly("ChIJ-test-place", null);
	}

	private void cell(long cellId, String area, String legalDong) {
		jdbc.sql("""
				insert into cell (cell_id, area, legal_dong, legal_dong_en,
					center_lat, center_lng, sw_lat, sw_lng, ne_lat, ne_lng)
				values (?, ?, ?, ?, 37.57, 126.98, 37.5698, 126.9797, 37.5702, 126.9803)
				""")
			.params(cellId, area, legalDong, legalDong)
			.update();
	}

	private void store(
		long id,
		long cellId,
		String name,
		String category,
		String foodType,
		String licenseDate,
		boolean franchise,
		boolean active) {

		jdbc.sql("""
				insert into store (id, mgmt_no, cell_id, name, category, food_type, franchise,
					road_addr, lat, lng, license_date, active)
				values (?, ?, ?, ?, ?, ?, ?, '서울특별시 종로구', 37.57, 126.98, cast(? as date), ?)
				""")
			.params(id, "mgmt-" + id, cellId, name, category, foodType, franchise, licenseDate, active)
			.update();
	}
}
