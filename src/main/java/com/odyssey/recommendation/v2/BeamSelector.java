package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.odyssey.cell.GeoPoint;
import com.odyssey.recommendation.v2.CandidatePool.LegCandidates;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;

final class BeamSelector implements CourseSelector {

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
			List<Path> next = expand(beam, slot, pool, request.excludeIds());
			if (next.isEmpty()) {
				Interruption interruption = new Interruption(slot.slot(), "NO_VERIFIED_MATCH");
				return choose(beam, request.seed()).selection(interruption);
			}
			beam = next.stream().sorted(ORDER).limit(WIDTH).toList();
		}
		return choose(beam, request.seed()).selection(null);
	}

	private List<Path> expand(List<Path> beam, CourseSlot slot, CandidatePool pool, Set<Long> excluded) {
		LegCandidates firstLeg = pool.forLeg(slot, 0, V2RecommendationSelector.RADIUS_METERS, 0);
		LegCandidates betweenStops = pool.forLeg(slot, 150, 500, 100);
		List<Path> next = new ArrayList<>();
		for (Path path : beam) {
			LegCandidates leg = path.picks().isEmpty() ? firstLeg : betweenStops;
			List<Selected> nearby = leg.near(path.origin(), path.earlierStops(), path.excludedIds(excluded));
			addCandidatePaths(next, path, slot, nearby);
			if (slot.requirement().skipWhenMissing()) {
				String reason = nearby.isEmpty() ? "NO_VERIFIED_MATCH" : "SKIPPED_FOR_ROUTE";
				next.add(path.skip(slot, reason));
			}
		}
		return next;
	}

	private void addCandidatePaths(List<Path> next, Path path, CourseSlot slot, List<Selected> nearby) {
		int start = 0;
		while (start < nearby.size()) {
			double floor = nearby.get(start).candidate().percentile() - CheckOrder.BAND;
			int end = start + 1;
			while (end < nearby.size() && nearby.get(end).candidate().percentile() >= floor) {
				end++;
			}
			for (int index = start; index < end; index++) {
				next.add(path.visit(slot, nearby.get(index), index, end - start));
			}
			start = end;
		}
	}

	private Path choose(List<Path> beam, long seed) {
		Path best = beam.getFirst();
		int stopCount = best.picks().size();
		double minimumScore = best.score() - stopCount * CheckOrder.BAND;
		List<Path> nearBest = beam.stream()
			.filter(path -> path.picks().size() == stopCount && path.score() >= minimumScore)
			.toList();
		double shortest = nearBest.stream().mapToDouble(Path::distance).min().orElseThrow();
		List<Path> choices = nearBest.stream().filter(path -> path.distance() <= shortest + EXTRA_METERS).toList();
		return choices.get(new Random(seed).nextInt(choices.size()));
	}

	private record Path(GeoPoint origin, List<GeoPoint> visited, List<Pick> picks, List<Skipped> skipped,
		double score, double distance) {

		static Path start(GeoPoint origin) {
			return new Path(origin, List.of(), List.of(), List.of(), 0, 0);
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

		Path visit(CourseSlot slot, Selected selected, int index, int bandSize) {
			double percentile = selected.candidate().percentile();
			Pick pick = Pick.chosen(slot, index, Item.from(selected), false, bandSize, percentile);
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
