package com.odyssey.recommendation.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

/**
 * 한 구간에서 영업을 확인할 후보 순서. 외부 호출을 모르는 순수한 계산이다.
 *
 * <p>남은 후보 중 1위와 방문 백분위 {@link #BAND} 점 이내를 한 점수대로 보고 seed 로 섞어 내준다. 그 점수대를
 * 다 내주면 남은 후보의 다음 점수대로 내려간다. 모두 합쳐 {@link #MAX_CHECKS} 곳까지다.
 *
 * <p>점수대는 다음 후보를 달라고 할 때 섞는다. 미리 다 섞으면 {@link Random} 을 더 쓰게 되어, 같은 seed 로
 * 이어지는 뒤 구간의 순서가 바뀐다.
 */
final class CheckOrder implements Iterator<Integer> {

	/** 90점 이상 칸의 인접 월 백분위 변화는 중앙값 1.3점, 75%가 2.8점 이내였다(2025년 12개 배치). */
	static final double BAND = 3.0;
	/**
	 * 5번일 때 을지로 토요일 심야에서 3점 범위 7곳 중 영업하는 2곳에 닿기 전에 끊겼다(2026-11-21).
	 * 범위 하나는 대개 10곳 이하라 범위 전체를 확인할 수 있게 잡았다.
	 */
	static final int MAX_CHECKS = 10;

	private final List<Double> percentiles;
	private final List<Integer> remaining = new ArrayList<>();
	private final Deque<Integer> band = new ArrayDeque<>();
	private final Random random;
	private int given;
	private int filledBandSize;
	private int lastBandSize;

	private CheckOrder(List<Double> percentiles, Set<Integer> excluded, Random random) {
		this.percentiles = percentiles;
		this.random = random;
		for (int i = 0; i < percentiles.size(); i++) {
			if (!excluded.contains(i)) {
				remaining.add(i);
			}
		}
	}

	/**
	 * @param percentiles 목록 API 순서(백분위 내림차순)의 방문 백분위
	 * @param excluded    건너뛸 후보의 위치(0부터)
	 * @param random      점수대를 섞을 난수. 구간끼리 이어 쓴다
	 * @return 후보 위치(0부터)를 확인할 순서로 내주는 반복자
	 */
	static CheckOrder of(List<Double> percentiles, Set<Integer> excluded, Random random) {
		return new CheckOrder(List.copyOf(percentiles), Set.copyOf(excluded), random);
	}

	@Override
	public boolean hasNext() {
		if (given == MAX_CHECKS) {
			return false;
		}
		if (band.isEmpty() && !remaining.isEmpty()) {
			double floor = percentiles.get(remaining.getFirst()) - BAND;
			List<Integer> next = remaining.stream().filter(i -> percentiles.get(i) >= floor).toList();
			List<Integer> shuffled = new ArrayList<>(next);
			Collections.shuffle(shuffled, random);
			band.addAll(shuffled);
			filledBandSize = next.size();
			remaining.removeAll(next);
		}
		return !band.isEmpty();
	}

	@Override
	public Integer next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		given++;
		lastBandSize = filledBandSize;
		return band.removeFirst();
	}

	/**
	 * @return 마지막으로 내준 후보가 속한 점수대의 후보 수. 제외한 후보는 세지 않고, 확인 상한에 걸려 내주지 못한
	 *     후보는 센다. 아직 내준 후보가 없으면 0
	 */
	int bandSize() {
		return lastBandSize;
	}
}
