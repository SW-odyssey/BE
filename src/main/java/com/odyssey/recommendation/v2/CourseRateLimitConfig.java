package com.odyssey.recommendation.v2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.odyssey.global.ratelimit.RateLimitInterceptor;
import com.odyssey.global.ratelimit.RequestRateLimiter;

/**
 * 코스 추천 API 에 요청 한도를 건다. 동작과 근거는 docs/recommendation-v2/COURSE.md 의 "요청 한도".
 */
@Configuration
public class CourseRateLimitConfig implements WebMvcConfigurer {

	private final boolean enabled;
	private final int clientBurst;
	private final int clientPerMinute;
	private final int globalBurst;
	private final int globalPerMinute;

	public CourseRateLimitConfig(
		@Value("${rate-limit.course.enabled}") boolean enabled,
		@Value("${rate-limit.course.client-burst}") int clientBurst,
		@Value("${rate-limit.course.client-per-minute}") int clientPerMinute,
		@Value("${rate-limit.course.global-burst}") int globalBurst,
		@Value("${rate-limit.course.global-per-minute}") int globalPerMinute) {
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
			registry.addInterceptor(new RateLimitInterceptor(courseRateLimiter())).addPathPatterns(V2CourseController.PATH);
		}
	}
}
