package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.odyssey.cell.GeoPoint;
import com.odyssey.recommendation.v2.CandidatePool.LegCandidates;
import com.odyssey.recommendation.v2.CandidatePool.LegCandidates.Ranked;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;

/**
 * 자리마다 경로를 {@link #WIDTH} 개까지만 남기며 코스 전체를 한 번에 고른다. 정류장 수, 방문 백분위 합, 총 직선거리
 * 순으로 비교하므로 앞 자리에서 가까운 곳을 고르다 뒤 자리가 막히는 일을 줄인다.
 */
final class BeamSelector implements CourseSelector {

	/** 건너뛴 자리 조합마다 남기는 경로 수 */
	private static final int WIDTH = 300;
	private static final double EXTRA_METERS = 100;
	private static final Comparator<Path> ORDER = Comparator
		.comparingInt((Path path) -> path.picks().size()).reversed()
		.thenComparing(Comparator.comparingDouble(Path::score).reversed())
		.thenComparingDouble(Path::distance);

	@Override
	public Selection select(Request request, CandidatePool pool) {
		List<Path> beam = List.of(Path.start(request.origin()));
		for (CourseSlot slot : request.slots()) {
			List<Path> next = expand(beam, slot, pool, request);
			if (next.isEmpty()) {
				Interruption interruption = new Interruption(slot.slot(), "NO_VERIFIED_MATCH");
				return choose(beam, request.seed()).selection(interruption);
			}
			beam = prune(next);
		}
		return choose(beam, request.seed()).selection(null);
	}

	private List<Path> expand(List<Path> beam, CourseSlot slot, CandidatePool pool, Request request) {
		LegCandidates firstLeg = pool.forLeg(slot, 0, V2RecommendationSelector.RADIUS_METERS, 0);
		LegCandidates betweenStops = request.walk().betweenStops(pool, slot);
		Set<Long> excludedHere = request.excludedAt(slot.position());
		List<Path> next = new ArrayList<>();
		for (Path path : beam) {
			LegCandidates leg = path.picks().isEmpty() ? firstLeg : betweenStops;
			List<Ranked> nearby = leg.near(path.origin(), path.earlierStops(), path.excludedIds(excludedHere));
			addCandidatePaths(next, path, slot, nearby);
			if (slot.requirement().skipWhenMissing()) {
				String reason = nearby.isEmpty() ? "NO_VERIFIED_MATCH" : "SKIPPED_FOR_ROUTE";
				next.add(path.skip(slot, reason));
			}
		}
		return next;
	}

	private void addCandidatePaths(List<Path> next, Path path, CourseSlot slot, List<Ranked> nearby) {
		List<Double> percentiles = nearby.stream().map(row -> row.selected().candidate().percentile()).toList();
		for (List<Integer> band : BandShuffleSelector.groupByPercentileBand(percentiles, Set.of())) {
			for (int index : band) {
				Ranked candidate = nearby.get(index);
				next.add(path.visit(slot, candidate.selected(), candidate.rank(), band.size()));
			}
		}
	}

	/**
	 * 건너뛴 자리 조합마다 {@link #WIDTH} 개씩 남긴다. 건너뛴 경로는 정류장이 적어 늘 뒤로 밀리므로 한꺼번에
	 * 자르면, 선택 자리에 후보가 많을 때 건너뛰는 경로가 전부 사라져 뒤의 필수 자리를 채울 길이 끊긴다.
	 *
	 * @return {@link #ORDER} 로 정렬한 경로
	 */
	private static List<Path> prune(List<Path> paths) {
		return paths.stream()
			.collect(Collectors.groupingBy(Path::skippedPositions, LinkedHashMap::new, Collectors.toList()))
			.values().stream()
			.flatMap(group -> group.stream().sorted(ORDER).limit(WIDTH))
			.sorted(ORDER)
			.toList();
	}

	/** 정류장이 가장 많고 점수 합이 1위와 가까운 경로 중, 총 거리가 짧은 쪽에서 seed 로 하나를 고른다. */
	private Path choose(List<Path> beam, long seed) {
		List<Path> choices = shortestAmongNearBest(beam);
		return choices.get(new Random(seed).nextInt(choices.size()));
	}

	private List<Path> shortestAmongNearBest(List<Path> beam) {
		Path best = beam.getFirst();
		int stopCount = best.picks().size();
		double minimumScore = best.score() - stopCount * BandShuffleSelector.PERCENTILE_BAND;
		List<Path> nearBest = beam.stream()
			.filter(path -> path.picks().size() == stopCount && path.score() >= minimumScore)
			.toList();
		double shortest = nearBest.stream().mapToDouble(Path::distance).min().orElseThrow();
		return nearBest.stream().filter(path -> path.distance() <= shortest + EXTRA_METERS).toList();
	}

	private record Path(GeoPoint origin, List<GeoPoint> visited, List<Pick> picks, List<Skipped> skipped,
		double score, double distance) {

		static Path start(GeoPoint origin) {
			return new Path(origin, List.of(), List.of(), List.of(), 0, 0);
		}

		/** @return 건너뛴 자리 번호. 같은 조합의 경로끼리만 비교해 자르는 기준이다 */
		List<Integer> skippedPositions() {
			return skipped.stream().map(Skipped::position).toList();
		}

		List<GeoPoint> earlierStops() {
			return visited.isEmpty() ? List.of() : visited.subList(0, visited.size() - 1);
		}

		Set<Long> excludedIds(Set<Long> requested) {
			Set<Long> excluded = new HashSet<>(requested);
			for (Pick pick : picks) {
				excluded.add(pick.stop().place().id());
			}
			return excluded;
		}

		Path visit(CourseSlot slot, Selected selected, int rank, int bandSize) {
			double percentile = selected.candidate().percentile();
			Pick pick = Pick.chosen(slot, rank, Item.from(selected), false, bandSize, percentile);
			GeoPoint location = selected.location();
			return new Path(location, append(visited, location), append(picks, pick), skipped,
				score + percentile, distance + selected.distanceMeters());
		}

		Path skip(CourseSlot slot, String reason) {
			Skipped missing = new Skipped(slot.slot(), slot.position(), reason);
			return new Path(origin, visited, picks, append(skipped, missing), score, distance);
		}

		Selection selection(Interruption interruption) {
			return new Selection(picks, skipped, interruption);
		}

		private static <T> List<T> append(List<T> items, T item) {
			List<T> extended = new ArrayList<>(items);
			extended.add(item);
			return extended;
		}
	}
}
