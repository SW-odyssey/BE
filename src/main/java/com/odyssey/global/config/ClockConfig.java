package com.odyssey.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버의 "오늘"을 정하는 시계.
 *
 * <p>방문 날짜의 허용 범위와 평일 / 주말 판단은 서버가 도는 곳(Cloud Run 은 UTC)과 상관없이
 * 서울 기준이어야 한다. LocalDate.now() 를 직접 부르지 말고 이 Clock 을 주입받아
 * {@code LocalDate.now(clock)} 으로 쓴다. 테스트에서는 고정 시계로 바꿔 끼운다.
 */
@Configuration
public class ClockConfig {

	/**
	 * API 의 기준 시간대. meta 응답의 timeZone 으로도 나간다
	 */
	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}
}
