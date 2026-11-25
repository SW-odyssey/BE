package com.odyssey.global.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 요청 한 건을 받아도 되는지 정한다. 두 단계로 막는다.
 *
 * <ol>
 *   <li>클라이언트별 한도: 한 사용자(주소)가 반복해서 호출하는 것을 막는다.</li>
 *   <li>서버 전체 한도: 주소를 바꿔 가며 보내는 요청까지 합쳐서 외부 호출량의 상한을 둔다.
 *       클라이언트 주소는 요청 헤더에서 오므로 속일 수 있다. 비용 상한은 이 한도가 지킨다.</li>
 * </ol>
 */
public final class RequestRateLimiter {

	public enum Scope {
		CLIENT, GLOBAL
	}

	public record Decision(boolean allowed, long retryAfterSeconds, Scope scope) {

		static final Decision ALLOWED = new Decision(true, 0, null);

		static Decision denied(long waitNanos, Scope scope) {
			long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos + TimeUnit.SECONDS.toNanos(1) - 1));
			return new Decision(false, seconds, scope);
		}
	}

	private static final String OVERFLOW_KEY = "";
	private static final long SWEEP_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

	private final ConcurrentHashMap<String, TokenBucket> clients = new ConcurrentHashMap<>();
	private final TokenBucket global;
	private final int clientBurst;
	private final int clientPerMinute;
	private final int maxClients;
	private final LongSupplier nanoClock;
	private volatile long nextSweepNanos;

	public RequestRateLimiter(int clientBurst, int clientPerMinute, int globalBurst, int globalPerMinute) {
		this(clientBurst, clientPerMinute, globalBurst, globalPerMinute, 10_000, System::nanoTime);
	}

	RequestRateLimiter(int clientBurst, int clientPerMinute, int globalBurst, int globalPerMinute,
		int maxClients, LongSupplier nanoClock) {
		this.clientBurst = clientBurst;
		this.clientPerMinute = clientPerMinute;
		this.maxClients = maxClients;
		this.nanoClock = nanoClock;
		this.global = new TokenBucket(globalBurst, globalPerMinute, nanoClock);
		this.nextSweepNanos = nanoClock.getAsLong() + SWEEP_INTERVAL_NANOS;
		TokenBucket.requireValidLimit(clientBurst, clientPerMinute);
	}

	public Decision tryAcquire(String clientKey) {
		sweepIfDue();
		// 서버 전체 한도를 먼저 본다. 주소를 바꿔 가며 보내는 요청이 여기서 걸러져 클라이언트 버킷을 늘리지 못한다
		long wait = global.consumeOrWaitNanos();
		if (wait > 0) {
			return Decision.denied(wait, Scope.GLOBAL);
		}
		TokenBucket client = clients.computeIfAbsent(keyFor(clientKey),
			key -> new TokenBucket(clientBurst, clientPerMinute, nanoClock));
		wait = client.consumeOrWaitNanos();
		if (wait > 0) {
			// 클라이언트 한도에 걸린 요청이 서버 전체 한도의 몫까지 쓰면 안 된다
			global.refund();
			return Decision.denied(wait, Scope.CLIENT);
		}
		return Decision.ALLOWED;
	}

	/** 클라이언트 수가 상한에 닿으면 새 클라이언트는 하나의 공용 버킷으로 모아 메모리가 무한히 늘지 않게 한다. */
	private String keyFor(String clientKey) {
		String key = clientKey == null ? OVERFLOW_KEY : clientKey;
		return clients.containsKey(key) || clients.size() < maxClients ? key : OVERFLOW_KEY;
	}

	/** 가득 찬 버킷은 새로 만들어도 같은 상태라서 지워도 한도가 달라지지 않는다. */
	private void sweepIfDue() {
		long now = nanoClock.getAsLong();
		if (now - nextSweepNanos < 0) {
			return;
		}
		nextSweepNanos = now + SWEEP_INTERVAL_NANOS;
		clients.values().removeIf(TokenBucket::isFull);
	}

	int trackedClients() {
		return clients.size();
	}
}
