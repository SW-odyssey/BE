package com.odyssey.global.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.ratelimit.RequestRateLimiter.Decision;
import com.odyssey.global.ratelimit.RequestRateLimiter.Scope;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 컨트롤러가 호출되기 전에 요청 한도를 확인한다. 한도를 넘으면 컨트롤러와 서비스가 실행되지 않으므로
 * 외부 API(Google)를 부르기 전에 429 로 끝난다. 오류 본문은 {@link BusinessException} 이 공통 형식으로 만든다.
 *
 * <p>브라우저가 보내는 CORS 사전 요청(OPTIONS)은 실제 호출이 아니라서 세지 않는다.
 * 클라이언트는 {@link HttpServletRequest#getRemoteAddr()} 로 구분한다. Cloud Run 은
 * {@code server.forward-headers-strategy=framework} 설정으로 원래 요청자의 주소가 반영된다.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

	private final RequestRateLimiter limiter;

	public RateLimitInterceptor(RequestRateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (CorsUtils.isPreFlightRequest(request)) {
			return true;
		}
		Decision decision = limiter.tryAcquire(request.getRemoteAddr());
		if (decision.allowed()) {
			return true;
		}
		if (decision.scope() == Scope.GLOBAL) {
			// 서버 전체 한도에 걸렸다는 것은 요청이 한꺼번에 몰리고 있다는 신호다
			log.warn("서버 전체 요청 한도 초과: {}", request.getRequestURI());
		}
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
		throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
			"요청이 너무 많습니다. " + decision.retryAfterSeconds() + "초 뒤에 다시 시도해 주세요.");
	}
}
