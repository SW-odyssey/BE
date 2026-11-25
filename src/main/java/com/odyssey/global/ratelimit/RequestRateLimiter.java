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
 *
 * <p>상태를 메모리에 둔다. Cloud Run 인스턴스마다 따로 세므로 인스턴스가 N개이면 전체 한도는 최대 N배가 된다.
 */
public final class RequestRateLimiter {

	/** 거절한 한도의 종류. */
	public enum Scope {
		CLIENT, GLOBAL
	}

	/** 요청 허용 여부. 거절이면 몇 초 뒤에 다시 보내면 되는지와 어떤 한도에 걸렸는지를 담는다. */
	public record Decision(boolean allowed, long retryAfterSeconds, Scope scope) {

		static final Decision ALLOWED = new Decision(true, 0, null);

		static Decision denied(long waitNanos, Scope scope) {
			long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos + TimeUnit.SECONDS.toNanos(1) - 1));
			return new Decision(false, seconds, scope);
		}
	}

	private static final String OVERFLOW_KEY = "";
	private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);
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
		// 한도 값이 잘못되면 첫 요청이 아니라 기동할 때 실패하게 클라이언트 버킷도 한 번 만들어 본다
		new TokenBucket(clientBurst, clientPerMinute, nanoClock);
	}

	/**
	 * @param clientKey 요청을 보낸 쪽을 구분하는 값. 보통 클라이언트 주소다
	 */
	public Decision tryAcquire(String clientKey) {
		sweepIfDue();
		TokenBucket client = clients.computeIfAbsent(keyFor(clientKey),
			key -> new TokenBucket(clientBurst, clientPerMinute, nanoClock));
		long wait = client.tryConsume();
		if (wait > 0) {
			return Decision.denied(wait, Scope.CLIENT);
		}
		wait = global.tryConsume();
		if (wait > 0) {
			// 서버 전체 한도에 걸린 요청이 이 사용자의 몫까지 깎으면 안 된다
			client.refund();
			return Decision.denied(wait, Scope.GLOBAL);
		}
		return Decision.ALLOWED;
	}

	/** 클라이언트 수가 상한에 닿으면 새 클라이언트는 하나의 공용 버킷으로 모아 메모리가 무한히 늘지 않게 한다. */
	private String keyFor(String clientKey) {
		String key = clientKey == null ? OVERFLOW_KEY : clientKey;
		return clients.containsKey(key) || clients.size() < maxClients ? key : OVERFLOW_KEY;
	}

	/** 오래 안 쓰인 버킷은 이미 가득 찬 상태라서 지워도 한도가 달라지지 않는다. */
	private void sweepIfDue() {
		long now = nanoClock.getAsLong();
		if (now - nextSweepNanos < 0) {
			return;
		}
		nextSweepNanos = now + SWEEP_INTERVAL_NANOS;
		clients.values().removeIf(bucket -> bucket.idleNanos() >= IDLE_NANOS);
	}

	int trackedClients() {
		return clients.size();
	}
}
