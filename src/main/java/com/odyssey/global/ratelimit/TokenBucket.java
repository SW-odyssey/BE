package com.odyssey.global.ratelimit;

import java.util.function.LongSupplier;

/**
 * 토큰 버킷. 처음에는 {@code capacity}번을 연달아 허용하고, 그 뒤로는 분당 {@code perMinute}번의 속도로 토큰이 다시 찬다.
 *
 * <p>고정 구간(예: 분당 N번)으로 세면 구간이 바뀌는 순간에 두 배가 한꺼번에 들어올 수 있어서 이 방식을 쓴다.
 * 시계는 주입받아 테스트에서 시간을 직접 움직인다.
 */
final class TokenBucket {

	private static final double NANOS_PER_MINUTE = 60_000_000_000d;

	private final int capacity;
	private final double tokensPerNano;
	private final LongSupplier nanoClock;
	private double tokens;
	private long lastNanos;

	TokenBucket(int capacity, int perMinute, LongSupplier nanoClock) {
		if (capacity < 1 || perMinute < 1) {
			throw new IllegalArgumentException("capacity 와 perMinute 는 1 이상이어야 합니다.");
		}
		this.capacity = capacity;
		this.tokensPerNano = perMinute / NANOS_PER_MINUTE;
		this.nanoClock = nanoClock;
		this.tokens = capacity;
		this.lastNanos = nanoClock.getAsLong();
	}

	/**
	 * 토큰 하나를 쓴다.
	 *
	 * @return 0 이면 허용이다. 0 보다 크면 거절이며, 다음 토큰이 생길 때까지 남은 나노초다
	 */
	synchronized long tryConsume() {
		refill();
		if (tokens >= 1) {
			tokens -= 1;
			return 0;
		}
		return Math.max(1, (long) Math.ceil((1 - tokens) / tokensPerNano));
	}

	/** 다른 한도에 걸려 요청이 거절됐을 때, 이미 쓴 토큰을 돌려준다. */
	synchronized void refund() {
		refill();
		tokens = Math.min(capacity, tokens + 1);
	}

	synchronized boolean isFull() {
		refill();
		return tokens >= capacity;
	}

	private void refill() {
		long now = nanoClock.getAsLong();
		long elapsed = now - lastNanos;
		if (elapsed > 0) {
			tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
			lastNanos = now;
		}
	}
}
