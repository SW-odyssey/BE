package com.odyssey.recommendation.v2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

	@Test
	void filtersBeforePagingAndLocalizesCurrentNames() throws Exception {
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
		db.store(101, 101, "인사동 식당", "KOREAN", null, "area-place-1", true);
		db.store(102, 102, "명동 상점", "SHOP", null, "area-place-2", true);
		db.store(103, 102, "명동 술집", "BAR", null, "area-place-3", true);
		db.store(104, 102, "명동 카페", "CAFE", null, "area-place-4", true);
		db.store(105, 103, "방문 기준 미달 식당", "KOREAN", null, "area-place-5", true);
		jdbc.sql("""
			insert into store_name_translation (store_id, source_name, name_en, name_zh, name_ja)
			values (103, '명동 술집', 'Myeongdong Pub', '明洞酒馆', '明洞酒場'),
				(104, '예전 카페 이름', 'Old Cafe', '旧咖啡馆', '旧カフェ')
			""").update();

		mvc.perform(request().param("areaId", "myeongdong").param("categories", "food,cafe")
				.param("size", "1").param("lang", "en"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.items[0].id").value(103))
			.andExpect(jsonPath("$.data.items[0].name").value("Myeongdong Pub"))
			.andExpect(jsonPath("$.data.items[0].areaId").value("myeongdong"));

		mvc.perform(request().param("areaId", "myeongdong").param("page", "1")
				.param("size", "1").param("lang", "en"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.items[0].id").value(104))
			.andExpect(jsonPath("$.data.items[0].name").value("명동 카페"));

		mvc.perform(request().param("areaId", "all"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items[0].id").value(101))
			.andExpect(jsonPath("$.data.items.length()").value(3));

		mvc.perform(request().param("categories", "shop"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(102));

		mvc.perform(request().param("page", "2").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("NO_MATCHES"))
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@ParameterizedTest
	@CsvSource({"areaId, bukchon", "categories, 'food,'", "page, -1", "size, 31", "lang, fr", "lat, 37.5"})
	void rejectsInvalidFilters(String key, String value) throws Exception {
		mvc.perform(request().param(key, value))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	private static MockHttpServletRequestBuilder request() {
		return get("/api/v2/area-recommendations")
			.param("datasetId", DS).param("weekday", "wed").param("slot", "lunch");
	}
}
