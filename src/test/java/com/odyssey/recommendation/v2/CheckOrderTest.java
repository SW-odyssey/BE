package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.V2CourseService.candidateCheckOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.recommendation.v2.V2CourseService.BandCandidate;

/** {@link V2CourseService} 가 점수대를 나눠 섞고 상한까지 내주는지 확인한다. */
class CheckOrderTest {

	private static List<Integer> all(Iterator<BandCandidate> order) {
		List<Integer> result = new ArrayList<>();
		order.forEachRemaining(candidate -> result.add(candidate.index()));
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
			firsts.add(candidateCheckOrder(percentiles, Set.of(), new Random(seed)).next().index());
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
		List<Integer> first = all(candidateCheckOrder(percentiles, Set.of(), new Random(seed)));
		List<Integer> second = all(candidateCheckOrder(percentiles, Set.of(), new Random(seed)));

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
		List<Integer> order = all(candidateCheckOrder(percentiles(values), Set.of(), new Random(3)));

		// then
		assertThat(new HashSet<>(order.subList(0, first.size()))).isEqualTo(first);
		assertThat(new HashSet<>(order.subList(first.size(), order.size()))).isEqualTo(second);
	}

	@ParameterizedTest(name = "[{0}] 제외 {1} → {2}")
	@CsvSource({"99 98 95, 0, 1 2", "99 98 95, 1, 0 2", "99 98 95, 0 1, 2", "99 98 95, '', 0 1 2"})
	@DisplayName("일부 후보 제외 → 남은 후보만 한 번씩 내준다")
	void skipsExcluded(String values, String excluded, String expected) {
		// when
		List<Integer> order = all(candidateCheckOrder(percentiles(values), indices(excluded), new Random(1)));

		// then
		assertThat(order).hasSize(indices(expected).size());
		assertThat(new HashSet<>(order)).isEqualTo(indices(expected));
	}

	@Test
	@DisplayName("1위를 제외하면 → 남은 1위와 정확히 3점 낮은 후보도 첫 후보가 될 수 있다")
	void recalculatesFirstBandAfterExcludingLeader() {
		// given
		List<Double> percentiles = List.of(99.0, 98.0, 95.0);
		Random swapPair = new Random() {
			@Override
			public int nextInt(int bound) {
				return 0;
			}
		};

		// when
		int firstCandidate = candidateCheckOrder(percentiles, Set.of(0), swapPair).next().index();

		// then
		assertThat(firstCandidate).isEqualTo(2);
	}

	@Test
	@DisplayName("모든 후보를 제외 → 내줄 후보가 없다")
	void allExcluded() {
		// when
		Iterator<BandCandidate> order = candidateCheckOrder(List.of(99.0, 98.0), Set.of(0, 1), new Random(1));

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
		List<Integer> order = all(candidateCheckOrder(percentiles, Set.of(), new Random(seed)));

		// then
		assertThat(order).hasSize(10).doesNotContain(10, 11);
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
		candidateCheckOrder(percentiles, Set.of(), random).next();

		// then
		assertThat(random.nextLong()).isEqualTo(reference.nextLong());
	}

	@Test
	@DisplayName("1위 점수대 3곳 중 하나를 꺼냄 → 점수대 후보 수는 3이다")
	void bandSizeOfFirstBand() {
		// given
		Iterator<BandCandidate> order = candidateCheckOrder(List.of(99.0, 97.5, 96.2, 90.0), Set.of(), new Random(1));

		// when
		BandCandidate candidate = order.next();

		// then
		assertThat(candidate.bandSize()).isEqualTo(3);
	}

	@Test
	@DisplayName("점수대 안의 제외 후보 → 점수대 후보 수에서 빠진다")
	void bandSizeSkipsExcluded() {
		// given
		Iterator<BandCandidate> order = candidateCheckOrder(List.of(99.0, 97.5, 96.2, 90.0), Set.of(1), new Random(1));

		// when
		BandCandidate candidate = order.next();

		// then
		assertThat(candidate.bandSize()).isEqualTo(2);
	}

	@Test
	@DisplayName("첫 점수대를 다 꺼내고 다음 점수대로 내려감 → 다음 점수대의 후보 수다")
	void bandSizeOfNextBand() {
		// given
		Iterator<BandCandidate> order = candidateCheckOrder(List.of(99.0, 98.0, 90.0), Set.of(), new Random(1));
		order.next();
		order.next();

		// when
		BandCandidate candidate = order.next();

		// then
		assertThat(candidate.index()).isEqualTo(2);
		assertThat(candidate.bandSize()).isEqualTo(1);
	}

	@Test
	@DisplayName("점수대의 마지막 후보를 꺼낸 뒤 hasNext 를 부름 → 꺼낸 후보의 점수대 후보 수를 그대로 둔다")
	void bandSizeStableAfterHasNext() {
		// given
		Iterator<BandCandidate> order = candidateCheckOrder(List.of(99.0, 98.0, 90.0), Set.of(), new Random(1));
		order.next();
		BandCandidate candidate = order.next();

		// when
		order.hasNext();

		// then
		assertThat(candidate.bandSize()).isEqualTo(2);
	}

	@Test
	@DisplayName("?? ???? 12?? ?? ? 10?? ???? ??? ??? 12?")
	void countsWholeBandBeyondCheckLimit() {
		Iterator<BandCandidate> order = candidateCheckOrder(Collections.nCopies(12, 99.0), Set.of(), new Random(1));
		List<BandCandidate> candidates = new ArrayList<>();
		order.forEachRemaining(candidates::add);

		assertThat(candidates).hasSize(10).extracting(BandCandidate::bandSize).containsOnly(12);
	}
}
