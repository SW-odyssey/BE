package com.odyssey.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import com.odyssey.health.HealthController;
import com.odyssey.metadata.MetaController;
import com.odyssey.recommendation.CellRecommendationController;
import com.odyssey.recommendation.CellRecommendationService;
import com.odyssey.recommendation.RecommendationController;
import com.odyssey.recommendation.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest({RecommendationController.class, CellRecommendationController.class,
	MetaController.class, HealthController.class})
@Import({SwaggerConfig.class, ClockConfig.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
	SpringDocWebMvcConfiguration.class})
class SwaggerDocumentationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private RecommendationService recommendationService;

	@MockitoBean
	private CellRecommendationService cellRecommendationService;

	@ParameterizedTest
	@MethodSource("filterOptions")
	@DisplayName("Swagger에서 추천 필터의 허용값과 기본값을 선택할 수 있다")
	void filterChoices(String name, String defaultValue, List<String> choices) throws Exception {
		// given
		JsonNode document = document();

		// when
		JsonNode parameter = parameter(document, name);

		// then
		assertThat(parameter.path("required").asBoolean()).isFalse();
		assertThat(parameter.path("description").asText()).isNotBlank();
		assertThat(parameter.at("/schema/default").asText()).isEqualTo(defaultValue);
		assertThat(parameter.at("/schema/enum").valueStream().map(JsonNode::asText).toList())
			.containsExactlyElementsOf(choices);
	}

	static Stream<Arguments> filterOptions() {
		return Stream.of(
			Arguments.of("areaId", "all",
				List.of("all", "jongno", "ikseon", "insadong", "euljiro", "myeongdong")),
			Arguments.of("category", "all", List.of("all", "food", "cafe")),
			Arguments.of("foodType", "any", List.of("any", "korean", "bar", "other")),
			Arguments.of("since", "false", List.of("false", "true")),
			Arguments.of("slot", "afternoon", List.of("lunch", "afternoon", "dinner", "late_night")),
			Arguments.of("density", "any", List.of("any", "less_busy", "lively")));
	}

	@Test
	@DisplayName("방문일에 만료되는 기본 날짜를 넣지 않고 입력 제한을 문서화한다")
	void inputConstraints() throws Exception {
		// given
		JsonNode document = document();

		// when
		JsonNode date = parameter(document, "visitDate");

		// then
		assertThat(date.at("/schema/format").asText()).isEqualTo("date");
		assertThat(date.at("/schema/default").isMissingNode()).isTrue();
		assertThat(date.at("/schema/example").isMissingNode()).isTrue();
		assertThat(date.path("description").asText()).contains("서울", "6일", "생략하면 오늘");
		assertThat(parameter(document, "q").at("/schema/maxLength").asInt()).isEqualTo(80);
		assertThat(parameter(document, "lat").at("/schema/minimum").asDouble()).isEqualTo(-90);
		assertThat(parameter(document, "lat").at("/schema/maximum").asDouble()).isEqualTo(90);
		assertThat(parameter(document, "lng").at("/schema/minimum").asDouble()).isEqualTo(-180);
		assertThat(parameter(document, "lng").at("/schema/maximum").asDouble()).isEqualTo(180);
	}

	@Test
	@DisplayName("추천 성공 응답은 실제 결과 스키마를 가리키고 오류 예시를 구분한다")
	void recommendationResponses() throws Exception {
		// given
		JsonNode document = document();

		// when
		JsonNode responses = document.at("/paths/~1api~1v1~1recommendations/get/responses");
		JsonNode wrapper = resolve(document,
			responses.at("/200/content/application~1json/schema"));
		JsonNode data = resolve(document, wrapper.at("/properties/data"));

		// then
		assertThat(data.at("/properties/total/maximum").asInt()).isEqualTo(30);
		assertThat(data.at("/properties/items/maxItems").asInt()).isEqualTo(30);
		assertThat(data.at("/properties/items/items/$ref").asText()).isNotBlank();
		assertThat(responses.at("/400/description").asText()).contains("INVALID_INPUT");
		assertThat(responses.at("/503/description").asText()).contains("DATA_UNAVAILABLE");
		assertThat(responses.at("/500/description").asText()).contains("INTERNAL_ERROR");
		assertThat(responses.at("/400/content/application~1json/example").toString())
			.contains("INVALID_INPUT", "success", "false");
	}

	@Test
	@DisplayName("응답의 열거값을 실제 JSON과 같은 소문자로 안내한다")
	void lowercaseResponseValues() throws Exception {
		// given
		JsonNode document = document();

		// when
		JsonNode item = document.at("/components/schemas/Item/properties");
		JsonNode applied = document.at("/components/schemas/Applied/properties");

		// then
		assertThat(item.at("/category/enum").valueStream().map(JsonNode::asText).toList())
			.containsExactly("food", "cafe");
		assertThat(item.at("/foodType/enum").valueStream().map(JsonNode::asText).toList())
			.containsExactly("korean", "bar", "other");
		assertThat(item.at("/badges/items/enum").valueStream().map(JsonNode::asText).toList())
			.containsExactly("century_store", "orae_gage", "future_heritage");
		assertThat(applied.at("/dayType/enum").valueStream().map(JsonNode::asText).toList())
			.containsExactly("mon_thu", "fri", "sat", "sun");
	}

	@Test
	@DisplayName("셀 경계 API는 필수 ID 목록과 최대 30개 제한을 문서화한다")
	void cellEndpoint() throws Exception {
		// given
		JsonNode document = document();

		// when
		JsonNode operation = document.at("/paths/~1api~1v1~1recommendations~1cell/get");
		JsonNode parameter = operation.path("parameters").get(0);
		JsonNode wrapper = resolve(document,
			operation.at("/responses/200/content/application~1json/schema"));
		JsonNode data = resolve(document, wrapper.at("/properties/data"));

		// then
		assertThat(parameter.path("name").asText()).isEqualTo("cellIds");
		assertThat(parameter.path("required").asBoolean()).isTrue();
		assertThat(parameter.at("/schema/type").asText()).isEqualTo("array");
		assertThat(parameter.at("/schema/minItems").asInt()).isEqualTo(1);
		assertThat(parameter.at("/schema/maxItems").asInt()).isEqualTo(30);
		assertThat(data.at("/properties/cells/items/$ref").asText()).isNotBlank();
	}

	private JsonNode document() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json);
	}

	private static JsonNode parameter(JsonNode document, String name) {
		return document.at("/paths/~1api~1v1~1recommendations/get/parameters").valueStream()
			.filter(parameter -> parameter.path("name").asText().equals(name)).findFirst().orElseThrow();
	}

	private static JsonNode resolve(JsonNode document, JsonNode schema) {
		String reference = schema.path("$ref").asText();
		return reference.startsWith("#/") ? document.at(reference.substring(1)) : schema;
	}
}
