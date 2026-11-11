package com.odyssey.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cellslotscore.entity.DayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 별도 PostgreSQL에서 실제 요청·필터·추천 정렬·페이지 응답을 함께 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class RecommendationPaginationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Clock clock;

	@Test
	@DisplayName("61곳을 요청하면 전체 추천 순서대로 30·30·1곳을 중복 없이 반환한다")
	void successivePages() throws Exception {
		// given: 낮은 점수의 1~30번보다 높은 점수의 31~61번이 먼저 와야 한다.
		LocalDate visitDate = LocalDate.now(clock);
		seed(visitDate);
		List<Long> expectedIds = Stream.concat(LongStream.rangeClosed(31, 61).boxed(),
			LongStream.rangeClosed(1, 30).boxed()).toList();

		// when
		JsonNode first = page(visitDate, null);
		JsonNode second = page(visitDate, 1);
		JsonNode last = page(visitDate, 2);
		JsonNode beyondLast = page(visitDate, 3);
		List<Long> actualIds = Stream.of(first, second, last)
			.flatMap(result -> result.path("items").valueStream())
			.map(item -> item.path("id").longValue()).toList();

		// then
		assertThat(actualIds).containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
		assertPage(first, 0, 30, true);
		assertPage(second, 1, 30, true);
		assertPage(last, 2, 1, false);
		assertPage(beyondLast, 3, 0, false);
		assertThat(second.at("/items/0/id").longValue()).isEqualTo(61);
		assertThat(second.at("/items/0/badges/0").stringValue()).isEqualTo("orae_gage");
	}

	private void seed(LocalDate date) {
		jdbc.sql("insert into legal_dong values ('인사동', 'INSADONG')").update();
		jdbc.sql("""
			insert into cell values
			(1, '인사동', 37.56, 126.97, 37.57, 126.98),
			(2, '인사동', 37.57, 126.98, 37.58, 126.99)
			""").update();
		jdbc.sql("""
			insert into cell_slot_score
			(cell_id, month, day_type, time_slot, density_level, peak_hour, score)
			values (1, ?, ?, 'AFTERNOON', 'LESS_BUSY', false, 100),
			(2, ?, ?, 'AFTERNOON', 'LESS_BUSY', false, 10)
			""").params(date.getMonthValue(), DayType.from(date).name(),
			date.getMonthValue(), DayType.from(date).name()).update();
		for (int id = 62; id >= 1; id--) {
			jdbc.sql("""
				insert into store (id, mgmt_no, cell_id, name, kind, lat, lng, google_place_id, active)
				values (?, ?, ?, ?, ?, 37.57, 126.98, ?, true)
				""").params(id, "pagination-" + id, id <= 30 ? 2 : 1, "가게" + id,
				id == 62 ? "CAFE" : "KOREAN", "place-" + id).update();
		}
		jdbc.sql("insert into store_badge values (61, 'ORAE_GAGE')").update();
	}

	private JsonNode page(LocalDate date, Integer page) throws Exception {
		var request = get("/api/v1/recommendations")
			.param("visitDate", date.toString()).param("category", "food");
		if (page != null) {
			request.param("page", page.toString());
		}
		String json = mockMvc.perform(request).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		JsonNode response = objectMapper.readTree(json);
		assertThat(response.path("success").booleanValue()).isTrue();
		return response.path("data");
	}

	private static void assertPage(JsonNode response, int page, int count, boolean hasNext) {
		assertThat(response.path("page").intValue()).isEqualTo(page);
		assertThat(response.path("total").intValue()).isEqualTo(count);
		assertThat(response.path("items").size()).isEqualTo(count);
		assertThat(response.path("hasNext").booleanValue()).isEqualTo(hasNext);
	}
}
