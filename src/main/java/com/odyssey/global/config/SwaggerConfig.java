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
			U+ 유동인구 기반의 음식점·카페 추천 API입니다.
			먼저 /api/v1/meta에서 선택지와 방문 날짜 범위를 확인하고,
			/api/v1/recommendations에서 Try it out으로 조건을 고른 뒤 Execute로 실행하세요.
			조건을 생략하면 오늘·오후·전체 지역으로 추천하며, 한 페이지는 최대 30곳입니다.
			page=0부터 요청하고 hasNext=true이면 같은 조건으로 다음 페이지를 조회하세요.
			응답은 {success, data, error} 형식이며 날짜 기준은 Asia/Seoul입니다.
			""");
		info.setVersion("v0.0.1");

		OpenAPI openAPI = new OpenAPI();
		openAPI.setInfo(info);

		return openAPI;
	}
}
