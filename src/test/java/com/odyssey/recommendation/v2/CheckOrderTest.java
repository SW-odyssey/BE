package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link CheckOrder} 가 점수대를 나눠 섞고 상한까지 내주는지 확인한다. */
class CheckOrderTest {

	private static List<Integer> all(CheckOrder order) {
		List<Integer> result = new ArrayList<>();
		order.forEachRemaining(result::add);
		return result;
	}

	private static List<Double> percentiles(String values) {
		return Arrays.stream(values.split(" ")).map(Double::valueOf).toList();
	}

	private static Set<Integer> indices(String values) {
		if (values.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(values.split(" ")).map(Integer::valueOf).collect(Collectors.toSet());
	}

	@Test
	@DisplayName("1위와 3점 이내 후보가 여럿 → 그 안에서만 seed 에 따라 첫 후보가 달라진다")
	void firstPickStaysWithinBand() {
		// given
		List<Double> percentiles = List.of(99.0, 97.5, 96.2, 95.9);

		// when
		Set<Integer> firsts = new HashSet<>();
		for (long seed = 1; seed <= 40; seed++) {
			firsts.add(CheckOrder.of(percentiles, Set.of(), new Random(seed)).next());
		}

		// then
		assertThat(firsts).containsExactlyInAnyOrder(0, 1, 2);
	}

	@ParameterizedTest(name = "seed {0}")
	@ValueSource(longs = {1, 7, 42})
	@DisplayName("같은 seed → 같은 순서다")
	void sameSeedSameOrder(long seed) {
		// given
		List<Double> percentiles = List.of(99.0, 98.0, 97.0, 90.0, 89.0);

		// when
		List<Integer> first = all(CheckOrder.of(percentiles, Set.of(), new Random(seed)));
		List<Integer> second = all(CheckOrder.of(percentiles, Set.of(), new Random(seed)));

		// then
		assertThat(first).isEqualTo(second);
	}

	@ParameterizedTest(name = "[{0}] → 첫 점수대 {1}, 다음 점수대 {2}")
	@CsvSource({"99 90 89, 0, 1 2", "99 97 90 88, 0 1, 2 3", "99 96 95.9, 0 1, 2"})
	@DisplayName("한 점수대를 다 내줌 → 남은 후보의 1위 기준으로 다음 점수대를 섞어 내준다")
	void nextBandAfterFirst(String values, String firstBand, String secondBand) {
		// given
		Set<Integer> first = indices(firstBand);
		Set<Integer> second = indices(secondBand);

		// when
		List<Integer> order = all(CheckOrder.of(percentiles(values), Set.of(), new Random(3)));

		// then
		assertThat(new HashSet<>(order.subList(0, first.size()))).isEqualTo(first);
		assertThat(new HashSet<>(order.subList(first.size(), order.size()))).isEqualTo(second);
	}

	@ParameterizedTest(name = "[{0}] 제외 {1} → {2}")
	@CsvSource({"99 98 95, 0, 1 2", "99 98 95, 1, 0 2", "99 98 95, 0 1, 2", "99 98 95, '', 0 1 2"})
	@DisplayName("제외한 후보 → 내주지 않고, 남은 후보로 점수대를 잡는다")
	void skipsExcluded(String values, String excluded, String expected) {
		// when
		List<Integer> order = all(CheckOrder.of(percentiles(values), indices(excluded), new Random(1)));

		// then
		assertThat(order).hasSize(indices(expected).size());
		assertThat(new HashSet<>(order)).isEqualTo(indices(expected));
	}

	@Test
	@DisplayName("모든 후보를 제외 → 내줄 후보가 없다")
	void allExcluded() {
		// when
		CheckOrder order = CheckOrder.of(List.of(99.0, 98.0), Set.of(0, 1), new Random(1));

		// then
		assertThat(order.hasNext()).isFalse();
	}

	@ParameterizedTest(name = "seed {0}")
	@ValueSource(longs = {1, 2, 3})
	@DisplayName("후보가 10곳보다 많음 → 10곳까지만 내주고 점수가 낮은 후보는 남는다")
	void capsAtMaxChecks(long seed) {
		// given: 99점 7곳, 90점 3곳, 80점 2곳
		List<Double> percentiles = new ArrayList<>();
		percentiles.addAll(Collections.nCopies(7, 99.0));
		percentiles.addAll(Collections.nCopies(3, 90.0));
		percentiles.addAll(Collections.nCopies(2, 80.0));

		// when
		List<Integer> order = all(CheckOrder.of(percentiles, Set.of(), new Random(seed)));

		// then
		assertThat(order).hasSize(CheckOrder.MAX_CHECKS).doesNotContain(10, 11);
	}

	@Test
	@DisplayName("첫 후보만 꺼냄 → 다음 점수대는 섞지 않아 같은 난수를 뒤 구간이 이어 쓴다")
	void shufflesNextBandOnlyWhenAsked() {
		// given
		List<Double> percentiles = List.of(99.0, 98.0, 90.0, 89.0);
		Random random = new Random(5);
		Random reference = new Random(5);
		Collections.shuffle(new ArrayList<>(List.of(0, 1)), reference);

		// when
		CheckOrder.of(percentiles, Set.of(), random).next();

		// then
		assertThat(random.nextLong()).isEqualTo(reference.nextLong());
	}
}
