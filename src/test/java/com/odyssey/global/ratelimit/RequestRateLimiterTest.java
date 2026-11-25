package com.odyssey.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.global.ratelimit.RequestRateLimiter.Decision;
import com.odyssey.global.ratelimit.RequestRateLimiter.Scope;

class RequestRateLimiterTest {

	private final AtomicLong now = new AtomicLong(1_000);

	private RequestRateLimiter limiter(int clientBurst, int clientPerMinute, int globalBurst, int globalPerMinute) {
		return new RequestRateLimiter(clientBurst, clientPerMinute, globalBurst, globalPerMinute, 10_000, now::get);
	}

	@Test
	@DisplayName("한 클라이언트가 burst 를 넘겨 보내면 거절하고 몇 초 뒤에 다시 보낼지 알려 준다")
	void deniesClientOverBurst() {
		// given
		RequestRateLimiter limiter = limiter(2, 6, 100, 600);
		limiter.tryAcquire("a");
		limiter.tryAcquire("a");

		// when
		Decision decision = limiter.tryAcquire("a");

		// then
		assertThat(decision.allowed()).isFalse();
		assertThat(decision.scope()).isEqualTo(Scope.CLIENT);
		assertThat(decision.retryAfterSeconds()).isEqualTo(10);
	}

	@Test
	@DisplayName("한 클라이언트가 한도를 써도 다른 클라이언트는 영향받지 않는다")
	void clientsAreIndependent() {
		// given
		RequestRateLimiter limiter = limiter(1, 6, 100, 600);
		limiter.tryAcquire("a");

		// when
		Decision other = limiter.tryAcquire("b");

		// then
		assertThat(other.allowed()).isTrue();
	}

	@Test
	@DisplayName("서버 전체 한도를 넘으면 클라이언트가 달라도 거절한다")
	void deniesWhenGlobalLimitReached() {
		// given
		RequestRateLimiter limiter = limiter(10, 60, 2, 6);
		limiter.tryAcquire("a");
		limiter.tryAcquire("b");

		// when
		Decision decision = limiter.tryAcquire("c");

		// then
		assertThat(decision.allowed()).isFalse();
		assertThat(decision.scope()).isEqualTo(Scope.GLOBAL);
	}

	@Test
	@DisplayName("서버 전체 한도에 걸려 거절된 요청은 그 클라이언트의 몫을 깎지 않는다")
	void globalDenialDoesNotConsumeClientToken() {
		// given: 서버 전체는 1번, 클라이언트는 2번까지
		RequestRateLimiter limiter = limiter(2, 6, 1, 6);
		limiter.tryAcquire("a");
		assertThat(limiter.tryAcquire("b").scope()).isEqualTo(Scope.GLOBAL);

		// when: 10초 뒤 서버 전체 토큰이 다시 찼다
		now.addAndGet(TimeUnit.SECONDS.toNanos(10));

		// then: b 는 거절당한 요청 때문에 한도를 잃지 않았다
		assertThat(limiter.tryAcquire("b").allowed()).isTrue();
	}

	@Test
	@DisplayName("시간이 지나면 거절됐던 클라이언트도 다시 허용한다")
	void allowsAgainAfterWaiting() {
		// given
		RequestRateLimiter limiter = limiter(1, 6, 100, 600);
		limiter.tryAcquire("a");
		Decision denied = limiter.tryAcquire("a");

		// when
		now.addAndGet(TimeUnit.SECONDS.toNanos(denied.retryAfterSeconds()));

		// then
		assertThat(limiter.tryAcquire("a").allowed()).isTrue();
	}

	@Test
	@DisplayName("오래 안 쓰인 클라이언트는 기록에서 지운다")
	void sweepsIdleClients() {
		// given
		RequestRateLimiter limiter = limiter(1, 6, 100, 600);
		limiter.tryAcquire("a");
		limiter.tryAcquire("b");
		assertThat(limiter.trackedClients()).isEqualTo(2);

		// when: 11분 뒤 다른 클라이언트가 요청한다
		now.addAndGet(TimeUnit.MINUTES.toNanos(11));
		limiter.tryAcquire("c");

		// then
		assertThat(limiter.trackedClients()).isEqualTo(1);
	}

	@Test
	@DisplayName("기록하는 클라이언트 수가 상한에 닿으면 새 클라이언트는 공용 한도로 모은다")
	void collapsesNewClientsBeyondLimit() {
		// given: 최대 2명만 따로 기록한다
		RequestRateLimiter limiter = new RequestRateLimiter(1, 6, 100, 600, 2, now::get);
		limiter.tryAcquire("a");
		limiter.tryAcquire("b");

		// when: 세 번째, 네 번째 클라이언트가 온다
		Decision third = limiter.tryAcquire("c");
		Decision fourth = limiter.tryAcquire("d");

		// then: 둘은 같은 공용 버킷을 나눠 쓰므로 두 번째가 거절된다
		assertThat(third.allowed()).isTrue();
		assertThat(fourth.allowed()).isFalse();
		assertThat(limiter.trackedClients()).isEqualTo(3);
	}

	@Test
	@DisplayName("주소를 알 수 없는 요청도 공용 한도로 세어 통과시키지 않는다")
	void unknownClientsShareOneBucket() {
		// given
		RequestRateLimiter limiter = limiter(1, 6, 100, 600);
		limiter.tryAcquire(null);

		// when
		Decision decision = limiter.tryAcquire(null);

		// then
		assertThat(decision.allowed()).isFalse();
	}
}
