package com.odyssey.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

	private final AtomicLong now = new AtomicLong(1_000);

	@Test
	@DisplayName("처음에는 burst 만큼 연달아 허용하고 그다음 요청은 거절한다")
	void allowsBurstThenDenies() {
		// given
		TokenBucket bucket = new TokenBucket(3, 6, now::get);

		// when
		long first = bucket.consumeOrWaitNanos();
		long second = bucket.consumeOrWaitNanos();
		long third = bucket.consumeOrWaitNanos();
		long fourth = bucket.consumeOrWaitNanos();

		// then
		assertThat(first).isZero();
		assertThat(second).isZero();
		assertThat(third).isZero();
		assertThat(fourth).isPositive();
	}

	@Test
	@DisplayName("거절되면 다음 토큰이 생길 때까지 남은 시간을 알려 준다")
	void reportsWaitUntilNextToken() {
		// given: 분당 6번이면 10초에 한 번 채워진다
		TokenBucket bucket = new TokenBucket(1, 6, now::get);
		bucket.consumeOrWaitNanos();

		// when
		long wait = bucket.consumeOrWaitNanos();

		// then
		assertThat(wait).isEqualTo(TimeUnit.SECONDS.toNanos(10));
	}

	@Test
	@DisplayName("시간이 지나면 속도에 맞춰 토큰이 다시 차서 허용한다")
	void refillsOverTime() {
		// given
		TokenBucket bucket = new TokenBucket(1, 6, now::get);
		bucket.consumeOrWaitNanos();
		assertThat(bucket.consumeOrWaitNanos()).isPositive();

		// when: 10초가 지남
		now.addAndGet(TimeUnit.SECONDS.toNanos(10));

		// then
		assertThat(bucket.consumeOrWaitNanos()).isZero();
		assertThat(bucket.consumeOrWaitNanos()).isPositive();
	}

	@Test
	@DisplayName("오래 기다려도 capacity 보다 많이 쌓이지 않는다")
	void doesNotExceedCapacity() {
		// given
		TokenBucket bucket = new TokenBucket(2, 60, now::get);
		bucket.consumeOrWaitNanos();
		bucket.consumeOrWaitNanos();

		// when: 한 시간이 지남
		now.addAndGet(TimeUnit.HOURS.toNanos(1));

		// then
		assertThat(bucket.consumeOrWaitNanos()).isZero();
		assertThat(bucket.consumeOrWaitNanos()).isZero();
		assertThat(bucket.consumeOrWaitNanos()).isPositive();
	}

	@Test
	@DisplayName("돌려받은 토큰은 다시 쓸 수 있지만 capacity 를 넘지 않는다")
	void refundRestoresToken() {
		// given
		TokenBucket bucket = new TokenBucket(1, 1, now::get);
		bucket.consumeOrWaitNanos();

		// when
		bucket.refund();
		bucket.refund();

		// then
		assertThat(bucket.consumeOrWaitNanos()).isZero();
		assertThat(bucket.consumeOrWaitNanos()).isPositive();
	}

	@Test
	@DisplayName("쓰지 않은 버킷은 가득 차 있다")
	void newBucketIsFull() {
		// given
		TokenBucket bucket = new TokenBucket(2, 6, now::get);

		// when & then
		assertThat(bucket.isFull()).isTrue();
	}

	@Test
	@DisplayName("토큰을 쓰면 가득 차지 않고, 시간이 지나 다 채워지면 다시 가득 찬다")
	void isFullAgainAfterRefill() {
		// given: 분당 6번이면 10초에 한 번 채워진다
		TokenBucket bucket = new TokenBucket(2, 6, now::get);
		bucket.consumeOrWaitNanos();
		bucket.consumeOrWaitNanos();

		// when & then
		assertThat(bucket.isFull()).isFalse();
		now.addAndGet(TimeUnit.SECONDS.toNanos(10));
		assertThat(bucket.isFull()).isFalse();
		now.addAndGet(TimeUnit.SECONDS.toNanos(10));
		assertThat(bucket.isFull()).isTrue();
	}

	@Test
	@DisplayName("capacity 나 속도가 1 보다 작으면 만들 수 없다")
	void rejectsInvalidSettings() {
		// when & then
		assertThatThrownBy(() -> new TokenBucket(0, 1, now::get)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TokenBucket(1, 0, now::get)).isInstanceOf(IllegalArgumentException.class);
	}
}
