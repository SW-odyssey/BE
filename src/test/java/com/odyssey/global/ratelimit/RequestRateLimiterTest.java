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
	@DisplayName("클라이언트 한도에 걸려 거절된 요청은 서버 전체 한도의 몫을 쓰지 않는다")
	void clientDenialDoesNotConsumeGlobalToken() {
		// given: 서버 전체는 2번, 클라이언트는 1번까지
		RequestRateLimiter limiter = limiter(1, 6, 2, 6);
		limiter.tryAcquire("a");
		assertThat(limiter.tryAcquire("a").scope()).isEqualTo(Scope.CLIENT);

		// when: 거절된 a 의 요청이 전체 한도를 썼다면 b 가 막힌다
		Decision other = limiter.tryAcquire("b");

		// then
		assertThat(other.allowed()).isTrue();
	}

	@Test
	@DisplayName("서버 전체 한도에 걸려 거절된 요청은 클라이언트 기록을 만들지 않는다")
	void globalDenialDoesNotTrackClient() {
		// given
		RequestRateLimiter limiter = limiter(10, 60, 1, 6);
		limiter.tryAcquire("a");

		// when: 주소를 바꿔 가며 보낸다
		limiter.tryAcquire("b");
		limiter.tryAcquire("c");

		// then
		assertThat(limiter.trackedClients()).isEqualTo(1);
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
	@DisplayName("한도가 다 채워진 클라이언트는 기록에서 지운다")
	void sweepsFullClients() {
		// given
		RequestRateLimiter limiter = limiter(1, 6, 100, 600);
		limiter.tryAcquire("a");
		limiter.tryAcquire("b");
		assertThat(limiter.trackedClients()).isEqualTo(2);

		// when: 한 번 더 채워지고도 남을 시간이 지난 뒤 다른 클라이언트가 요청한다
		now.addAndGet(TimeUnit.MINUTES.toNanos(2));
		limiter.tryAcquire("c");

		// then
		assertThat(limiter.trackedClients()).isEqualTo(1);
	}

	@Test
	@DisplayName("아직 덜 채워진 클라이언트는 청소 시각이 지나도 기록에 남긴다")
	void keepsClientsStillRefilling() {
		// given: 분당 1번이라 두 토큰을 다 채우려면 2분이 걸린다
		RequestRateLimiter limiter = limiter(2, 1, 100, 600);
		limiter.tryAcquire("a");
		limiter.tryAcquire("a");

		// when: 청소 시각(1분)은 지났지만 아직 토큰이 하나만 찼다
		now.addAndGet(TimeUnit.SECONDS.toNanos(70));
		limiter.tryAcquire("b");

		// then: a 의 사용 기록이 지워지면 한도보다 많이 받게 된다
		assertThat(limiter.trackedClients()).isEqualTo(2);
	}

	@Test
	@DisplayName("Retry-After 는 남은 시간을 초 단위로 올림한다")
	void roundsRetryAfterUp() {
		// when & then
		assertThat(Decision.denied(TimeUnit.SECONDS.toNanos(10), Scope.CLIENT).retryAfterSeconds()).isEqualTo(10);
		assertThat(Decision.denied(TimeUnit.SECONDS.toNanos(10) + 1, Scope.CLIENT).retryAfterSeconds()).isEqualTo(11);
		assertThat(Decision.denied(1, Scope.CLIENT).retryAfterSeconds()).isEqualTo(1);
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
