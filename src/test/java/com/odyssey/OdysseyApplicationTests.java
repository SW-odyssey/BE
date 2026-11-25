package com.odyssey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// local 프로파일(docker compose 자동 기동, SQL trace 로그)을 피하려고 test 로 고정한다
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OdysseyApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = {"/api/v1/meta", "/api/v1/recommendations", "/api/v1/recommendations/cell"})
	@DisplayName("폐기한 v1 추천 경로는 404를 반환한다")
	void removedRecommendationsReturnNotFound(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Swagger에는 v2 추천 경로가 있고 v1 추천 경로는 없다")
	void swaggerDocumentsV2Recommendations() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v2/meta'].get").exists())
			.andExpect(jsonPath("$.paths['/api/v2/recommendations'].get").exists())
			.andExpect(jsonPath("$.paths['/api/v2/places/{id}'].get").exists())
			.andExpect(jsonPath("$.paths['/api/v2/course-recommendations'].get").exists())
			.andExpect(jsonPath("$.paths['/api/v1/meta']").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/v1/recommendations']").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/v1/recommendations/cell']").doesNotExist());
	}
}
