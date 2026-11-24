package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.odyssey.recommendation.v2.CourseHoursLookup.Hours;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;

/**
 * 계획 선택기가 짠 코스를 Google 영업시간으로 확인한다. 닫힌 매장이 있으면 그 매장을 그 자리에서 빼고 다시 짠다.
 *
 * <p>영업시간 조회는 요청마다 같은 Google 장소를 한 번만 하고, 서로 다른 장소는 {@link #MAX_LOOKUPS} 곳까지만 본다.
 */
final class VerifiedCourseSelector implements CourseSelector {

	static final int MAX_LOOKUPS = 40;

	private final CourseSelector planner;
	private final CourseHoursLookup lookup;

	VerifiedCourseSelector(CourseSelector planner, CourseHoursLookup lookup) {
		this.planner = planner;
		this.lookup = lookup;
	}

	@Override
	public Selection select(Request request, CandidatePool pool) {
		Map<String, Hours> hoursByPlace = new HashMap<>();
		if (!request.previousStopIds().isEmpty()) {
			Set<Long> avoided = new HashSet<>(request.excludeIds());
			avoided.addAll(request.previousStopIds());
			Request replacement = new Request(request.slots(), request.origin(), avoided, request.weekday(),
				request.seed(), request.walk(), request.excludedBySlot());
			// 부분 교체로 넘어갈 때 정류장마다 한 곳을 확인할 수 있도록 조회 여유를 남긴다.
			int replacementLookupLimit = MAX_LOOKUPS - request.slots().size();
			Optional<Selection> full = new VisitVerification(replacement, hoursByPlace, replacementLookupLimit)
				.selectVerifiedCourse(pool, request.previousStopIds().size());
			if (full.isPresent()) {
				return full.get();
			}
		}
		return new VisitVerification(request, hoursByPlace, MAX_LOOKUPS).selectVerifiedCourse(pool, 0)
			.orElseThrow();
	}

	/** 한 계획의 한 정류장을 확인한 결과. */
	private sealed interface Verdict {
		/** 영업이 확인돼 코스에 넣는다. */
		record Accepted(Pick pick) implements Verdict {
		}

		/** 이 정류장을 빼고 코스를 다시 짠다. */
		record Replan() implements Verdict {
		}

		/** 더 확인하지 않고 이 자리에서 코스를 멈춘다. */
		record Interrupt(CourseSlot slot, String reason) implements Verdict {
		}
	}

	/** 요청 하나를 처리하는 동안만 쓰는 상태. 조회한 영업시간과 다시 짤 때 뺄 매장을 모은다. */
	private final class VisitVerification {
		private final Request request;
		/** 영업시간을 확인할 수 없는 매장처럼 어느 자리에서도 뺄 매장 */
		private final Set<Long> excluded;
		/** 자리 번호 → 그 자리에서 영업하지 않아 뺀 매장. 같은 매장이 다른 시각의 자리에서는 열 수 있다 */
		private final Map<Integer, Set<Long>> closedAtSlot = new HashMap<>();
		private final List<CourseSlot> remaining;
		private final Map<String, Hours> hoursByPlace;
		private final int lookupLimit;
		private final List<Skipped> unavailableSlots = new ArrayList<>();

		VisitVerification(Request request, Map<String, Hours> hoursByPlace, int lookupLimit) {
			this.request = request;
			this.hoursByPlace = hoursByPlace;
			this.lookupLimit = lookupLimit;
			this.excluded = new HashSet<>(request.excludeIds());
			request.excludedBySlot().forEach((position, ids) -> closedAtSlot.put(position, new HashSet<>(ids)));
			this.remaining = new ArrayList<>(request.slots());
		}

		Optional<Selection> selectVerifiedCourse(CandidatePool pool, int minimumStops) {
			while (true) {
				Selection plan = planner.select(retryRequest(), pool);
				if (!meetsTarget(plan, minimumStops)) {
					return Optional.empty();
				}
				Optional<Selection> verified = verify(plan);
				if (verified.isPresent()) {
					return verified.filter(course -> meetsTarget(course, minimumStops));
				}
			}
		}

		private boolean meetsTarget(Selection plan, int minimumStops) {
			return minimumStops == 0 || (plan.interruption() == null && plan.picks().size() >= minimumStops);
		}

		private Request retryRequest() {
			return new Request(remaining, request.origin(), excluded, request.weekday(), request.seed(),
				request.walk(), request.previousStopIds(), closedAtSlot);
		}

		/** @return 확인을 마친 코스. 계획을 버리고 다시 짜야 하면 빈 값 */
		private Optional<Selection> verify(Selection plan) {
			List<Pick> verified = new ArrayList<>();
			for (Pick pick : plan.picks()) {
				switch (judge(pick)) {
					case Verdict.Accepted accepted -> verified.add(accepted.pick());
					case Verdict.Replan replan -> {
						return Optional.empty();
					}
					case Verdict.Interrupt stop -> {
						return Optional.of(interrupted(plan, verified, stop.slot(), stop.reason()));
					}
				}
			}
			return Optional.of(new Selection(verified, skipsBefore(plan, endOfPlan(plan)), plan.interruption()));
		}

		private Verdict judge(Pick pick) {
			Stop stop = pick.stop();
			Item place = stop.place();
			CourseSlot slot = request.slotAt(stop.position());
			String placeId = place.googlePlaceId();
			if (placeId == null || placeId.isBlank()) {
				excluded.add(place.id());
				return new Verdict.Replan();
			}
			if (!hoursByPlace.containsKey(placeId) && hoursByPlace.size() >= lookupLimit) {
				return new Verdict.Interrupt(slot, "CHECK_LIMIT");
			}
			Hours hours = hoursByPlace.computeIfAbsent(placeId, lookup::load);
			Result result = hours.during(request.weekday(), slot.visitTime(), slot.stayMinutes(place.kind()));
			if (result == Result.UNAVAILABLE) {
				return lookupFailed(slot);
			}
			if (!result.allowsVisit(place.kind())) {
				closedAtSlot.computeIfAbsent(slot.position(), position -> new HashSet<>()).add(place.id());
				return new Verdict.Replan();
			}
			return new Verdict.Accepted(Pick.chosen(slot, stop.candidateRank(), place,
				result == Result.HOURS_UNKNOWN, pick.bandSize(), pick.percentile()));
		}

		/** 조회 실패는 매장 탓이 아니다. 필수 자리면 멈추고, 선택 자리면 그 자리를 건너뛰고 다시 짠다. */
		private Verdict lookupFailed(CourseSlot slot) {
			if (!slot.requirement().skipWhenMissing()) {
				return new Verdict.Interrupt(slot, "VERIFICATION_UNAVAILABLE");
			}
			remaining.remove(slot);
			unavailableSlots.add(new Skipped(slot.slot(), slot.position(), "VERIFICATION_UNAVAILABLE"));
			return new Verdict.Replan();
		}

		/**
		 * @return 계획이 끝난 자리 번호. 계획이 필수 자리에서 멈췄으면 그 자리 번호이고, 끝까지 갔으면 끝없이 크다
		 */
		private int endOfPlan(Selection plan) {
			if (plan.interruption() == null) {
				return Integer.MAX_VALUE;
			}
			int handledSlots = plan.picks().size() + plan.skipped().size();
			return handledSlots < remaining.size() ? remaining.get(handledSlots).position() : Integer.MAX_VALUE;
		}

		private Selection interrupted(Selection plan, List<Pick> verified, CourseSlot slot, String reason) {
			return new Selection(verified, skipsBefore(plan, slot.position()), new Interruption(slot.slot(), reason));
		}

		/** 앞 자리 중 건너뛴 자리. 계획이 건너뛴 자리와 영업 조회가 실패해 뺀 자리를 합친다. */
		private List<Skipped> skipsBefore(Selection plan, int position) {
			List<Skipped> skipped = new ArrayList<>(plan.skipped());
			skipped.addAll(unavailableSlots);
			return skipped.stream()
				.filter(slot -> slot.position() < position)
				.sorted(Comparator.comparingInt(Skipped::position))
				.toList();
		}
	}
}
