package com.odyssey.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.odyssey.global.ratelimit.RateLimitInterceptor;
import com.odyssey.global.ratelimit.RequestRateLimiter;

/**
 * 코스 추천 API 의 요청 한도.
 *
 * <p>코스 요청 하나는 Google Place Details 를 여러 번 부르고 호출마다 요금이 붙는다.
 * 한도를 넘는 요청은 Google 을 부르기 전에 429 로 끝낸다. 값은 application.yaml 의
 * {@code rate-limit.course.*} 에서 읽고, Cloud Run 환경변수({@code RATE_LIMIT_COURSE_CLIENT_BURST} 등)로 바꿀 수 있다.
 *
 * <pre>{@code
 * rate-limit:
 *   course:
 *     enabled: true
 *     client-burst: 10
 *     client-per-minute: 12
 *     global-burst: 30
 *     global-per-minute: 60
 * }</pre>
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

	static final String COURSE_PATH = "/api/v2/course-recommendations";

	private final boolean enabled;
	private final int clientBurst;
	private final int clientPerMinute;
	private final int globalBurst;
	private final int globalPerMinute;

	public RateLimitConfig(
		@Value("${rate-limit.course.enabled:true}") boolean enabled,
		@Value("${rate-limit.course.client-burst:10}") int clientBurst,
		@Value("${rate-limit.course.client-per-minute:12}") int clientPerMinute,
		@Value("${rate-limit.course.global-burst:30}") int globalBurst,
		@Value("${rate-limit.course.global-per-minute:60}") int globalPerMinute) {
		this.enabled = enabled;
		this.clientBurst = clientBurst;
		this.clientPerMinute = clientPerMinute;
		this.globalBurst = globalBurst;
		this.globalPerMinute = globalPerMinute;
	}

	@Bean
	public RequestRateLimiter courseRateLimiter() {
		return new RequestRateLimiter(clientBurst, clientPerMinute, globalBurst, globalPerMinute);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if (enabled) {
			registry.addInterceptor(new RateLimitInterceptor(courseRateLimiter())).addPathPatterns(COURSE_PATH);
		}
	}
}
