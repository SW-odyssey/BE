package com.odyssey.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI odysseyOpenAPI() {
		Info info = new Info();
		info.setTitle("Odyssey API");
		info.setDescription("""
			U+ 유동인구로 음식점·카페를 추천하는 API입니다. 앱 화면은 v2(/api/v2)를 씁니다.

			**용어**
			- 배치(dataset): 한 달 치 유동인구를 분석한 결과 묶음입니다. 달마다 하나씩 있고 datasetId로 구분합니다.
			- 칸(cell): 지도를 50m 격자로 나눈 한 칸입니다. cellId가 칸 번호입니다. 매장은 모두 어느 한 칸에 속합니다.

			**v2 호출 순서**
			1. GET /api/v2/meta: 배치 목록, 서비스 지역, 시간대 경계 같은 선택지를 받습니다.
			2. GET /api/v2/recommendations: 고른 배치의 id를 datasetId로 넣어 내 위치 주변 추천 매장을 받습니다.
			3. GET /api/v2/cells/reasons: 2번 목록에 나온 cellId를 모아 추천 이유를 한 번에 받습니다.
			4. 매장 카드를 누르면 GET /api/v2/places/{id}(매장 정보)와 GET /api/v2/cells/{cellId}/visit-pattern(방문 그래프)을 부릅니다.

			2~4번에는 같은 datasetId를 보냅니다. 그 이유는 2번 API 설명에 있습니다.

			**Try it out**
			예시 값이 미리 들어 있어 Execute만 누르면 실제 응답을 볼 수 있습니다.
			예시의 datasetId는 배치를 새로 올리면 없어질 수 있습니다. 404 DATASET_NOT_FOUND가 나오면 1번 meta에서 새 id를 받아 넣으세요.

			**공통 규칙**
			- 응답은 항상 {success, data, error} 모양입니다. 실패하면 success=false이고 error.code와 error.message가 옵니다.
			- 설명에 없는 쿼리 파라미터를 보내거나 같은 파라미터를 두 번 보내면 400입니다.
			- 문제가 여러 개면 400(요청 형식) → 404(없는 배치) → 503(서버 문제) 순서로 먼저 걸린 것 하나만 알려 줍니다.
			""");
		info.setVersion("v0.0.1");

		OpenAPI openAPI = new OpenAPI();
		openAPI.setInfo(info);

		return openAPI;
	}
}
