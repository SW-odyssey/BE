package com.odyssey.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 브라우저가 다른 도메인의 프론트엔드에서 이 API 를 호출할 수 있게 허용한다.
 *
 * <p>허용 주소는 application.yaml 의 {@code cors.allowed-origins} 에서 읽는다.
 * 쉼표로 여러 개를 넣을 수 있고 * 와일드카드도 쓸 수 있다.
 *
 * <pre>{@code
 * cors:
 *   allowed-origins: http://localhost:3000
 * }</pre>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	// 쉼표로 구분된 문자열을 Spring 이 배열로 잘라서 넣어 준다
	public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// 모든 경로에 적용
		CorsRegistration registration = registry.addMapping("/**");

		// Patterns 를 쓰면 정확한 주소와 * 와일드카드를 함께 넣을 수 있다
		registration.allowedOriginPatterns(allowedOrigins);

		// 조회 전용 API 라 GET 만 허용한다 (OPTIONS 는 사전 요청용)
		registration.allowedMethods("GET", "OPTIONS");

		registration.allowedHeaders("*");

		// 다른 도메인의 FE 가 429 응답의 Retry-After 를 읽으려면 노출해야 한다
		registration.exposedHeaders(HttpHeaders.RETRY_AFTER);

		// 사전 요청 결과를 1시간 동안 재사용해서 왕복을 줄인다
		registration.maxAge(3600);
	}
}
