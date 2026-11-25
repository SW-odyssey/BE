package com.odyssey.recommendation.v2;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.support.SplitRecommendationFixture;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class V2AreaRecommendationIntegrationTest {

	private static final String DS = "area-test";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void seedAreaRecommendations() {
		SplitRecommendationFixture db = new SplitRecommendationFixture(jdbc);
		db.cell(101);
		db.cell(102);
		db.cell(103);
		jdbc.sql("insert into legal_dong (name, area) values ('명동1가', 'MYEONGDONG')").update();
		jdbc.sql("update cell set legal_dong = '명동1가' where cell_id in (102, 103)").update();
		db.dataset(DS, "2025-12-01", "2025-12-31T00:00:00Z", true);
		jdbc.sql("""
			update recommendation_dataset set policy_version = ?, reason_version = ?, profile_version = ?
			where dataset_id = ?
			""").params(V2DatasetResolver.POLICY_VERSION, V2DatasetResolver.REASON_VERSION,
			V2DatasetResolver.PROFILE_VERSION, DS).update();
		db.condition(DS, 101, "wed", "lunch", "AVAILABLE", 99.0);
		db.condition(DS, 102, "wed", "lunch", "AVAILABLE", 90.0);
		db.condition(DS, 103, "wed", "lunch", "AVAILABLE", 74.99);
		db.store(110, 101, "인사동 식당", "KOREAN", null, "area-place-1", true);
		db.store(102, 102, "명동 상점", "SHOP", null, "area-place-2", true);
		db.store(104, 102, "명동 카페", "CAFE", null, "area-place-4", true);
		db.store(103, 102, "명동 술집", "BAR", null, "area-place-3", true);
		db.store(105, 103, "방문 기준 미달 식당", "KOREAN", null, "area-place-5", true);
		jdbc.sql("""
			insert into store_name_translation (store_id, source_name, name_en, name_zh, name_ja)
			values (103, '명동 술집', 'Myeongdong Pub', '明洞酒馆', '明洞酒場'),
				(104, '예전 카페 이름', 'Old Cafe', '旧咖啡馆', '旧カフェ')
			""").update();
	}

	@Test
	@DisplayName("명동과 음식점·카페를 선택한다 → 방문 기준을 통과한 명동 매장만 반환한다")
	void filtersRegionAndKinds() throws Exception {
		// given
		var req = request().param("areaId", "myeongdong").param("categories", "food,cafe");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(2))
			.andExpect(jsonPath("$.data.items[*].id", contains(103, 104)))
			.andExpect(jsonPath("$.data.items[*].areaId", contains("myeongdong", "myeongdong")));
	}

	@Test
	@DisplayName("명동 당일 후보의 방문 백분위가 같다 → 매장 ID 오름차순으로 반환한다")
	void breaksPercentileTiesByStoreId() throws Exception {
		// given
		var req = request().param("areaId", "myeongdong");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].id", contains(103, 104)));
	}

	@Test
	@DisplayName("전체 지역에서 ID가 큰 매장의 백분위가 높다 → 백분위가 높은 매장을 먼저 반환한다")
	void ordersAllAreasByPercentileBeforeStoreId() throws Exception {
		// given
		var req = request().param("areaId", "all");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items[*].id", contains(110, 103, 104)));
	}

	@Test
	@DisplayName("지역을 생략한다 → 서비스 전체 지역의 매장을 조회한다")
	void defaultsToAllAreas() throws Exception {
		// given
		var req = request();

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3));
	}

	@Test
	@DisplayName("영어를 요청한다 → 원본 상호와 일치하는 번역을 사용하고 이전 상호 번역은 제외한다")
	void localizesCurrentNamesOnly() throws Exception {
		// given
		var req = request().param("areaId", "myeongdong").param("lang", "en");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].name", contains("Myeongdong Pub", "명동 카페")));
	}

	@Test
	@DisplayName("첫 페이지 다음으로 이동한다 → 다음 후보를 반환하고 전체 개수는 그대로 유지한다")
	void movesToNextPage() throws Exception {
		// given
		var firstPage = request().param("areaId", "myeongdong").param("size", "1");
		var nextPage = request().param("areaId", "myeongdong").param("page", "1").param("size", "1");

		// when / then
		mvc.perform(firstPage)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.items[*].id", contains(103)));
		mvc.perform(nextPage)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.items[*].id", contains(104)));
	}

	@Test
	@DisplayName("쇼핑 카테고리를 명시한다 → 추천 조건에 맞는 상점을 반환한다")
	void includesExplicitShopCategory() throws Exception {
		// given
		var req = request().param("categories", "shop");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[*].id", contains(102)));
	}

	@Test
	@DisplayName("마지막 페이지를 넘긴다 → 전체 개수와 빈 목록을 반환한다")
	void returnsEmptyPageAfterLastPage() throws Exception {
		// given
		var req = request().param("page", "2").param("size", "2");

		// when / then
		mvc.perform(req)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NO_MATCHES"))
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@ParameterizedTest
	@DisplayName("지원하지 않는 필터 값을 보낸다 → HTTP 400을 반환한다")
	@CsvSource({"areaId, bukchon", "areaId, '  '", "areaId, ''", "categories, 'food,'",
		"page, -1", "size, 31", "lang, fr", "lat, 37.5"})
	void rejectsInvalidFilters(String key, String value) throws Exception {
		// given
		var req = request().param(key, value);

		// when / then
		mvc.perform(req)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	private static MockHttpServletRequestBuilder request() {
		return get("/api/v2/area-recommendations")
			.param("datasetId", DS).param("weekday", "wed").param("slot", "lunch");
	}
}
