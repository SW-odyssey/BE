package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.CourseReason.AreaType;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.OpenDuringStay;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.CourseReason.SimilarChoices;
import com.odyssey.recommendation.v2.CourseReasonFacts.Facts;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/** {@link V2CourseService} 의 코스 규칙. 후보는 미리 정한 값이고 영업 확인은 호출을 기록하는 가짜다. */
class V2CourseServiceTest {

	private static final GeoPoint ORIGIN = new GeoPoint(37.5660, 126.9900);

	private final CourseCandidates loader = mock(CourseCandidates.class);
	private final FakeChecker checker = new FakeChecker();
	private final CourseReasonFacts reasonFacts = mock(CourseReasonFacts.class);
	private final V2CourseService service = new V2CourseService(loader, new BandShuffleSelector(checker), reasonFacts);
	private final Map<TimeSlot, List<Candidate>> bySlot = new EnumMap<>(TimeSlot.class);

	/** 영업 확인 호출을 기록하고, 정해 둔 결과(기본 OPEN)를 돌려준다. */
	private static final class FakeChecker implements CourseVisitChecker {

		record Call(String placeId, Weekday weekday, LocalTime visitTime, int stayMinutes) {
		}

		private final Map<String, Result> results = new HashMap<>();
		private final List<Call> calls = new ArrayList<>();
		private Result fallback = Result.OPEN;

		@Override
		public Result check(String googlePlaceId, Weekday weekday, LocalTime visitTime, int stayMinutes) {
			calls.add(new Call(googlePlaceId, weekday, visitTime, stayMinutes));
			return results.getOrDefault(googlePlaceId, fallback);
		}
	}

	@BeforeEach
	void pool() {
		given(loader.load(anyString(), any(), any(), anyList())).willAnswer(call -> {
			V2Context context = new V2Context(call.getArgument(0), "2025-11", call.getArgument(1), call.getArgument(2));
			return new CandidatePool(context, bySlot);
		});
		given(reasonFacts.load(anyString(), any(), anyCollection())).willReturn(Facts.NONE);
	}

	/** 출발점 근처(매장 id 마다 약 11m 북쪽)에 둔다. id 20 까지는 출발점에서 300m 안이다. */
	private static Candidate store(long id, StoreKind kind, double percentile) {
		return at(id, kind, percentile, 37.5660 + id / 10_000.0, 126.9900);
	}

	private static Candidate at(long id, StoreKind kind, double percentile, double lat, double lng) {
		return at(id, id, kind, percentile, lat, lng);
	}

	private static Candidate at(long id, long cellId, StoreKind kind, double percentile, double lat, double lng) {
		return new Candidate(new Place(id, "매장" + id, cellId, kind, lat, lng, null, "place-" + id), "m" + id,
			percentile);
	}

	private void candidates(TimeSlot slot, Candidate... candidates) {
		bySlot.put(slot, List.of(candidates));
	}

	private V2CourseResponse recommend(TimeSlot startSlot, long seed) {
		return service.recommend("nov", Weekday.FRI, startSlot, ORIGIN, Set.of(), seed, StoreKind.CAFE, true);
	}

	@Test
	@DisplayName("기본 서비스 구성은 코스를 재탐색하고 영업이 확인된 매장에 추천 이유를 붙인다")
	void verifiesAndExplainsTheDefaultBeamCourse() {
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 85));
		CourseHoursLookup lookup = id -> CourseHoursLookup.Hours.fixed(id.equals("place-1") ? Result.NOT_OPEN : Result.OPEN);
		CourseSelector selector = new CourseSelectionConfig().courseSelector(lookup);
		V2CourseService configured = new V2CourseService(loader, selector, reasonFacts);

		V2CourseResponse course = configured.recommend("nov", Weekday.FRI, TimeSlot.DINNER, ORIGIN, Set.of(), 7);

		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).singleElement().satisfies(stop -> {
			assertThat(stop.place().id()).isEqualTo(2L);
			assertThat(stop.candidateRank()).isEqualTo(2);
			assertThat(stop.reasons()).anyMatch(OpenDuringStay.class::isInstance);
		});
		then(loader).should().load("nov", Weekday.FRI, TimeSlot.DINNER, List.of(TimeSlot.DINNER));
	}

	@Test
	@DisplayName("1위와 3점 이내 후보가 여럿 → seed 에 따라 그 안에서만 고른다")
	void picksWithinTopBand() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 97.5),
			store(3, StoreKind.KOREAN, 96.2), store(4, StoreKind.KOREAN, 95.9));

		// when
		Set<Long> picked = new HashSet<>();
		for (long seed = 1; seed <= 40; seed++) {
			picked.add(recommend(TimeSlot.DINNER, seed).stops().getFirst().place().id());
		}

		// then
		assertThat(picked).containsExactlyInAnyOrder(1L, 2L, 3L);
	}

	@Test
	@DisplayName("같은 seed → 같은 코스다")
	void sameSeedSameCourse() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 98));
		candidates(TimeSlot.LATE_NIGHT, store(3, StoreKind.BAR, 99), store(4, StoreKind.BAR, 98));

		// when
		V2CourseResponse first = recommend(TimeSlot.DINNER, 7);
		V2CourseResponse second = recommend(TimeSlot.DINNER, 7);

		// then
		assertThat(second).isEqualTo(first);
	}

	@Test
	@DisplayName("1위 점수대가 모두 닫혀 있음 → 다음 점수대에서 고르고 목록 순번을 담는다")
	void fallsToNextBand() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 90));
		checker.results.put("place-1", Result.NOT_OPEN);

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 3);

		// then
		assertThat(course.stops().getFirst().place().id()).isEqualTo(2L);
		assertThat(course.stops().getFirst().candidateRank()).isEqualTo(2);
	}

	@Test
	@DisplayName("다음 구간 → 처음 출발점이 아니라 앞에서 고른 매장 좌표 300m 안에서 찾는다")
	void nextStageStartsFromPickedStore() {
		// given: 동쪽 저녁 매장에서 약 265m, 처음 출발점에서는 약 485m 인 주점
		candidates(TimeSlot.DINNER, at(1, StoreKind.KOREAN, 99, 37.5660, 126.9925));
		candidates(TimeSlot.LATE_NIGHT, at(2, StoreKind.BAR, 99, 37.5660, 126.9955));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).extracting(stop -> stop.place().id()).containsExactly(1L, 2L);
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::walkMinutes).containsExactly(5, 6);
	}

	@Test
	@DisplayName("1위 점수대 안에서 영업하는 곳이 뒤에 있음 → 섞인 순서와 상관없이 찾는다")
	void findsOpenStoreLateInBand() {
		// given
		List<Candidate> ranked = new ArrayList<>();
		for (long id = 1; id <= 7; id++) {
			ranked.add(store(id, StoreKind.KOREAN, 99));
		}
		candidates(TimeSlot.DINNER, ranked.toArray(Candidate[]::new));
		checker.fallback = Result.NOT_OPEN;
		checker.results.put("place-7", Result.OPEN);

		// when
		Set<Long> picked = new HashSet<>();
		for (long seed = 1; seed <= 10; seed++) {
			picked.add(recommend(TimeSlot.DINNER, seed).stops().getFirst().place().id());
		}

		// then
		assertThat(picked).containsExactly(7L);
	}

	@Test
	@DisplayName("확인할 후보가 10곳보다 많고 모두 닫힘 → 10번만 확인하고 NO_VERIFIED_MATCH 로 멈춘다")
	void capsChecksPerStage() {
		// given: 99점 7곳, 90점 3곳, 80점 2곳
		List<Candidate> ranked = new ArrayList<>();
		for (long id = 1; id <= 12; id++) {
			ranked.add(store(id, StoreKind.KOREAN, id <= 7 ? 99 : id <= 10 ? 90 : 80));
		}
		candidates(TimeSlot.DINNER, ranked.toArray(Candidate[]::new));
		checker.fallback = Result.NOT_OPEN;

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.interruption().reason()).isEqualTo("NO_VERIFIED_MATCH");
		assertThat(checker.calls).hasSize(10)
			.extracting(FakeChecker.Call::placeId).doesNotContain("place-11", "place-12");
	}

	@Test
	@DisplayName("외부 조회가 실패함 → 다음 후보로 넘어가지 않고 VERIFICATION_UNAVAILABLE 로 멈춘다")
	void stopsWhenVerificationUnavailable() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 98));
		checker.fallback = Result.UNAVAILABLE;

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption().reason()).isEqualTo("VERIFICATION_UNAVAILABLE");
		assertThat(checker.calls).hasSize(1);
	}

	@Test
	@DisplayName("오후를 구경으로 고름 → 쇼핑 후보에서 찾고 카페는 보지 않는다")
	void afternoonShop() {
		// given
		candidates(TimeSlot.AFTERNOON, store(1, StoreKind.CAFE, 99), store(2, StoreKind.SHOP, 97));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.AFTERNOON, ORIGIN, Set.of(), 1,
			StoreKind.SHOP);

		// then
		assertThat(course.stops().getFirst().place().id()).isEqualTo(2L);
	}

	@Test
	@DisplayName("영업시간이 없는 쇼핑 → 받아서 hoursUnknown 으로 표시한다")
	void shopWithUnknownHours() {
		// given
		candidates(TimeSlot.AFTERNOON, store(1, StoreKind.SHOP, 97));
		checker.fallback = Result.HOURS_UNKNOWN;

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.AFTERNOON, ORIGIN, Set.of(), 1,
			StoreKind.SHOP);

		// then
		assertThat(course.stops().getFirst().hoursUnknown()).isTrue();
		assertThat(course.stops().getFirst().reasons()).noneMatch(OpenDuringStay.class::isInstance);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"LUNCH, KOREAN", "AFTERNOON, CAFE", "DINNER, KOREAN"})
	@DisplayName("영업시간이 없는 음식점·카페 → 받지 않고 NO_VERIFIED_MATCH 로 멈춘다")
	void nonShopWithUnknownHours(TimeSlot slot, StoreKind kind) {
		// given
		candidates(slot, store(1, kind, 97));
		checker.fallback = Result.HOURS_UNKNOWN;

		// when
		V2CourseResponse course = recommend(slot, 1);

		// then
		assertThat(course.stops()).isEmpty();
		assertThat(course.interruption().reason()).isEqualTo("NO_VERIFIED_MATCH");
	}

	@ParameterizedTest(name = "{0} {1} → {3} 도착 {4}분")
	@CsvSource({"LUNCH, KOREAN, CAFE, 12:00, 50", "AFTERNOON, CAFE, CAFE, 14:00, 80",
		"AFTERNOON, SHOP, SHOP, 14:00, 30", "DINNER, KOREAN, CAFE, 18:00, 60", "LATE_NIGHT, BAR, CAFE, 22:00, 70"})
	@DisplayName("구간마다 → 정해 둔 도착 시각과 머무는 시간을 영업 확인에 넘긴다")
	void passesVisitTimeAndStay(TimeSlot slot, StoreKind kind, StoreKind afternoon, LocalTime visitTime,
		int stayMinutes) {
		// given
		candidates(slot, store(1, kind, 99));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, slot, ORIGIN, Set.of(), 1, afternoon, true);

		// then
		assertThat(checker.calls.getFirst())
			.isEqualTo(new FakeChecker.Call("place-1", Weekday.FRI, visitTime, stayMinutes));
		assertThat(course.stops().getFirst().visitTime()).isEqualTo(visitTime.toString());
	}

	@Test
	@DisplayName("다음 구간 후보를 모두 제외함 → 앞 구간만 담은 PARTIAL 과 ALL_EXCLUDED 다")
	void excludedStoresGivePartialCourse() {
		// given
		candidates(TimeSlot.AFTERNOON, store(1, StoreKind.CAFE, 99));
		candidates(TimeSlot.DINNER, store(3, StoreKind.KOREAN, 99));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.AFTERNOON, ORIGIN, Set.of(3L, 4L),
			1, StoreKind.CAFE);

		// then
		assertThat(course.status()).isEqualTo("PARTIAL");
		assertThat(course.stops()).hasSize(1);
		assertThat(course.interruption()).isEqualTo(
			new V2CourseResponse.Interruption(TimeSlot.DINNER, "ALL_EXCLUDED"));
	}

	@Test
	@DisplayName("앞 구간에서 고른 매장 → 뒤 구간 후보에 다시 나와도 고르지 않는다")
	void pickedStoreIsNotReused() {
		// given: 같은 매장이 점심·저녁 후보에 모두 있다
		Candidate both = store(1, StoreKind.KOREAN, 99);
		candidates(TimeSlot.LUNCH, both);
		candidates(TimeSlot.AFTERNOON, store(2, StoreKind.CAFE, 99));
		candidates(TimeSlot.DINNER, at(1, StoreKind.KOREAN, 99, 37.5661, 126.9900));

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, 1);

		// then
		assertThat(course.interruption().reason()).isEqualTo("ALL_EXCLUDED");
	}

	@Test
	@DisplayName("첫 구간 후보가 없음 → NO_MATCHES 와 NO_NEARBY_MATCH 이고 context 는 시작 시간대다")
	void noCandidatesAtStart() {
		// given
		candidates(TimeSlot.DINNER);

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption().reason()).isEqualTo("NO_NEARBY_MATCH");
		assertThat(course.context().slot()).isEqualTo(TimeSlot.DINNER);
	}

	@ParameterizedTest
	@EnumSource(value = StoreKind.class, names = {"CAFE", "SHOP"}, mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("오후 자리가 카페·구경이 아님 → 후보를 읽기 전에 거절한다")
	void rejectsOtherAfternoonKinds(StoreKind afternoon) {
		// when
		IllegalArgumentException exception = catchThrowableOfType(IllegalArgumentException.class, () -> service.recommend("nov",
			Weekday.FRI, TimeSlot.AFTERNOON, ORIGIN, Set.of(), 1, afternoon));

		// then
		assertThat(exception).hasMessage("afternoon: " + afternoon);
		verifyNoInteractions(loader);
	}

	@Test
	@DisplayName("두 구간 코스 → 영업 확인이 다 끝난 뒤 고른 칸들로 이유 재료를 한 번만 읽는다")
	void loadsReasonFactsOnceAfterChecks() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 99));
		candidates(TimeSlot.LATE_NIGHT, store(2, StoreKind.BAR, 99));
		List<Integer> checksBeforeLoad = new ArrayList<>();
		given(reasonFacts.load(anyString(), any(), anyCollection())).willAnswer(call -> {
			checksBeforeLoad.add(checker.calls.size());
			return Facts.NONE;
		});

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.stops()).hasSize(2);
		assertThat(checksBeforeLoad).containsExactly(2);
		then(reasonFacts).should().load("nov", Weekday.FRI, List.of(1L, 2L));
	}

	@Test
	@DisplayName("재료가 모두 있음 → 칸 기준 백분위·체류 시각·점수대 수로 다섯 이유를 붙인다")
	void attachesAllReasons() {
		// given: 1위 점수대 2곳, 칸 1 은 저녁이 가장 붐비고 동네 유형이 있다
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 97.2), store(2, StoreKind.KOREAN, 96));
		checker.results.put("place-2", Result.NOT_OPEN);
		given(reasonFacts.load(anyString(), any(), anyCollection())).willReturn(
			new Facts(Map.of(1L, TimeSlot.DINNER), Map.of(1L, "late_night")));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.stops().getFirst().reasons()).containsExactly(new LocalTop(3),
			new OpenDuringStay("18:00", "19:00"), new PeakNow(), new AreaType("late_night"), new SimilarChoices(2));
	}

	@Test
	@DisplayName("고른 칸의 재료가 없음 → PEAK_NOW·AREA_TYPE 만 빠지고 코스는 그대로다")
	void missingFactsDropOnlyThoseReasons() {
		// given
		candidates(TimeSlot.LATE_NIGHT, store(1, StoreKind.BAR, 99));
		given(reasonFacts.load(anyString(), any(), anyCollection())).willReturn(
			new Facts(Map.of(9L, TimeSlot.LATE_NIGHT), Map.of(9L, "mixed")));

		// when
		V2CourseResponse course = recommend(TimeSlot.LATE_NIGHT, 1);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops().getFirst().reasons()).containsExactly(new LocalTop(1),
			new OpenDuringStay("22:00", "23:10"));
	}

	@Test
	@DisplayName("이유 재료 조회가 실패함 → 그 재료의 이유만 빠지고 코스는 그대로다")
	void failedFactsDoNotFailCourse() {
		// given
		candidates(TimeSlot.LATE_NIGHT, store(1, StoreKind.BAR, 99));
		given(reasonFacts.load(anyString(), any(), anyCollection())).willThrow(new QueryTimeoutException("timeout"));

		// when
		V2CourseResponse course = recommend(TimeSlot.LATE_NIGHT, 1);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops().getFirst().reasons()).containsExactly(new LocalTop(1),
			new OpenDuringStay("22:00", "23:10"));
	}

	@Test
	@DisplayName("놀거리 후보가 없음 → 카페에서 저녁으로 이어지고 건너뛴 두 자리만 기록한다")
	void continuesFromCafeWhenPlayIsMissing() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 99));
		candidates(TimeSlot.AFTERNOON, at(2, StoreKind.CAFE, 95, 37.5660, 126.9925));
		candidates(TimeSlot.DINNER, at(3, StoreKind.KOREAN, 95, 37.5660, 126.9955));
		candidates(TimeSlot.LATE_NIGHT, store(4, StoreKind.BAR, 99));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.LUNCH, ORIGIN, Set.of(), 7);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::position).containsExactly(1, 2, 4);
		assertThat(course.skipped()).containsExactly(
			new V2CourseResponse.Skipped(TimeSlot.AFTERNOON, 3, "NO_NEARBY_MATCH"),
			new V2CourseResponse.Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"));
		assertThat(course.interruption()).isNull();
		then(loader).should().load("nov", Weekday.FRI, TimeSlot.LUNCH,
			List.of(TimeSlot.LUNCH, TimeSlot.AFTERNOON, TimeSlot.DINNER));
	}

	@Test
	@DisplayName("심야를 선택하고 여섯 자리에 후보가 있음 → 선택 정류장에도 추천 이유를 붙인다")
	void completesSixStopsWithReasons() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 90));
		candidates(TimeSlot.AFTERNOON, store(2, StoreKind.CAFE, 90), store(3, StoreKind.PLAY, 65));
		candidates(TimeSlot.DINNER, store(4, StoreKind.KOREAN, 91), store(5, StoreKind.PLAY, 95));
		candidates(TimeSlot.LATE_NIGHT, store(6, StoreKind.BAR, 90));
		checker.results.put("place-3", Result.HOURS_UNKNOWN);

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, 7);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.skipped()).isEmpty();
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::position).containsExactly(1, 2, 3, 4, 5, 6);
		V2CourseResponse.Stop afternoonPlay = course.stops().get(2);
		assertThat(afternoonPlay.hoursUnknown()).isTrue();
		assertThat(afternoonPlay.reasons()).containsExactly(new LocalTop(35));
		assertThat(afternoonPlay.leaveTime()).isEqualTo("16:40");
		assertThat(course.stops().get(4).reasons()).containsExactly(new LocalTop(5), new OpenDuringStay("19:30", "20:30"));
	}

	@Test
	@DisplayName("심야 시작에 심야 선택을 생략 → 후보나 영업시간을 조회하지 않고 빈 코스를 반환한다")
	void omitsUnrequestedLateNight() {
		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.LATE_NIGHT, ORIGIN, Set.of(), 7);

		// then
		assertThat(course.context().slot()).isEqualTo(TimeSlot.LATE_NIGHT);
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).isEmpty();
		assertThat(course.skipped()).isEmpty();
		assertThat(checker.calls).isEmpty();
		then(loader).should().load("nov", Weekday.FRI, TimeSlot.LATE_NIGHT, List.of());
	}

	@Test
	@DisplayName("심야를 선택했지만 주점의 영업시간이 없음 → 6번 자리를 건너뛴다")
	void skipsUnverifiedLateNight() {
		// given
		candidates(TimeSlot.LATE_NIGHT, store(6, StoreKind.BAR, 90));
		checker.fallback = Result.HOURS_UNKNOWN;

		// when
		V2CourseResponse course = recommend(TimeSlot.LATE_NIGHT, 7);

		// then
		assertThat(course.skipped()).containsExactly(
			new V2CourseResponse.Skipped(TimeSlot.LATE_NIGHT, 6, "NO_VERIFIED_MATCH"));
		assertThat(course.interruption()).isNull();
	}

	@Test
	@DisplayName("이유 재료 조회의 트랜잭션을 못 엶(연결 확보 실패) → 코스와 정류장은 그대로고 그 재료의 이유만 빠진다")
	void failedTransactionDoesNotFailCourse() {
		// given
		candidates(TimeSlot.LATE_NIGHT, store(1, StoreKind.BAR, 99));
		given(reasonFacts.load(anyString(), any(), anyCollection()))
			.willThrow(new CannotCreateTransactionException("no connection"));

		// when
		V2CourseResponse course = recommend(TimeSlot.LATE_NIGHT, 1);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).hasSize(1);
		assertThat(course.stops().getFirst().reasons()).containsExactly(new LocalTop(1),
			new OpenDuringStay("22:00", "23:10"));
	}

	@Test
	@DisplayName("고른 곳이 없음 → 이유 재료를 읽지 않는다")
	void noFactsWithoutStops() {
		// given
		candidates(TimeSlot.DINNER);

		// when
		recommend(TimeSlot.DINNER, 1);

		// then
		then(reasonFacts).should(never()).load(anyString(), any(), anyCollection());
	}

	@Test
	@DisplayName("상위 점수대가 모두 닫혀 있다 → 부분 코스에도 실제 고른 점수대의 후보 수와 칸 백분위를 보낸다")
	void partialCourseUsesChosenBandForReasons() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 90),
			store(3, StoreKind.KOREAN, 88));
		checker.results.put("place-1", Result.NOT_OPEN);
		checker.results.put("place-2", Result.NOT_OPEN);

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, 1);

		// then
		assertThat(course.status()).isEqualTo("PARTIAL");
		assertThat(course.interruption().slot()).isEqualTo(TimeSlot.AFTERNOON);
		assertThat(course.stops().getFirst().candidateRank()).isEqualTo(3);
		assertThat(course.stops().getFirst().reasons()).containsExactly(new LocalTop(12),
			new OpenDuringStay("12:00", "12:50"), new SimilarChoices(2));
	}

	@Test
	@DisplayName("서로 다른 매장이 같은 칸에 있다 → 칸은 한 번만 조회하고 방문 시간대에 맞춰 이유를 붙인다")
	void sharesCellFactsAcrossStops() {
		// given
		long cellId = 3_000_000_001L;
		candidates(TimeSlot.DINNER, at(1, cellId, StoreKind.KOREAN, 97, 37.5660, 126.9900));
		candidates(TimeSlot.LATE_NIGHT, at(2, cellId, StoreKind.BAR, 91, 37.5661, 126.9900));
		given(reasonFacts.load("nov", Weekday.FRI, List.of(cellId))).willReturn(
			new Facts(Map.of(cellId, TimeSlot.LATE_NIGHT), Map.of(cellId, "mixed")));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		then(reasonFacts).should().load("nov", Weekday.FRI, List.of(cellId));
		then(reasonFacts).shouldHaveNoMoreInteractions();
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::reasons).containsExactly(
			List.of(new LocalTop(3), new OpenDuringStay("18:00", "19:00"), new AreaType("mixed")),
			List.of(new LocalTop(9), new OpenDuringStay("22:00", "23:10"), new PeakNow(), new AreaType("mixed")));
	}

	@Test
	@DisplayName("필수 자리 영업 확인이 실패한다 → 앞서 고른 칸만 조회해 부분 코스에 이유를 붙인다")
	void enrichesPartialCourseAfterRequiredSlotVerificationFailure() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 96));
		candidates(TimeSlot.AFTERNOON, store(2, StoreKind.CAFE, 99));
		checker.results.put("place-2", Result.UNAVAILABLE);
		given(reasonFacts.load("nov", Weekday.FRI, List.of(1L))).willReturn(
			new Facts(Map.of(1L, TimeSlot.LUNCH), Map.of()));

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, 1);

		// then
		assertThat(course.status()).isEqualTo("PARTIAL");
		assertThat(course.interruption()).isEqualTo(
			new V2CourseResponse.Interruption(TimeSlot.AFTERNOON, "VERIFICATION_UNAVAILABLE"));
		assertThat(course.stops()).singleElement().satisfies(stop -> {
			assertThat(stop.place().id()).isEqualTo(1L);
			assertThat(stop.reasons()).containsExactly(new LocalTop(4),
				new OpenDuringStay("12:00", "12:50"), new PeakNow());
		});
		then(reasonFacts).should().load("nov", Weekday.FRI, List.of(1L));
	}

	@Test
	@DisplayName("선택 자리(심야) 영업 확인이 실패한다 → 코스는 COMPLETE 이고 skipped 에 VERIFICATION_UNAVAILABLE 을 남긴다")
	void optionalSlotVerificationFailureIsSkipped() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 96));
		candidates(TimeSlot.LATE_NIGHT, store(2, StoreKind.BAR, 99));
		checker.results.put("place-2", Result.UNAVAILABLE);
		given(reasonFacts.load("nov", Weekday.FRI, List.of(1L))).willReturn(
			new Facts(Map.of(1L, TimeSlot.DINNER), Map.of()));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.interruption()).isNull();
		assertThat(course.stops()).singleElement().satisfies(stop -> {
			assertThat(stop.place().id()).isEqualTo(1L);
			assertThat(stop.reasons()).containsExactly(new LocalTop(4),
				new OpenDuringStay("18:00", "19:00"), new PeakNow());
		});
		assertThat(course.skipped()).containsExactly(
			new V2CourseResponse.Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"),
			new V2CourseResponse.Skipped(TimeSlot.LATE_NIGHT, 6, "VERIFICATION_UNAVAILABLE"));
		then(reasonFacts).should().load("nov", Weekday.FRI, List.of(1L));
	}

	@Test
	@DisplayName("저녁 둘째 자리(5번) → 주점은 받지 않고 놀거리만 받는다")
	void secondDinnerSlotRejectsBar() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 90), store(2, StoreKind.BAR, 95));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.DINNER, ORIGIN, Set.of(), 1,
			StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).extracting(stop -> stop.place().id()).containsExactly(1L);
		assertThat(course.skipped()).containsExactly(
			new V2CourseResponse.Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"));
	}

	@Test
	@DisplayName("저녁 첫째 자리(4번) → 방문 백분위 75 미만이면 후보가 아니다")
	void requiredSlotMinimumIs75() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 70));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, 1);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption()).isEqualTo(
			new V2CourseResponse.Interruption(TimeSlot.DINNER, "NO_NEARBY_MATCH"));
	}

	@Test
	@DisplayName("저녁 둘째 자리(5번) → 방문 백분위 75 미만인 놀거리는 건너뛰고 75 이상은 고른다")
	void secondDinnerSlotMinimumIs75() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 90), store(2, StoreKind.PLAY, 70));
		V2CourseResponse below = service.recommend("nov", Weekday.FRI, TimeSlot.DINNER, ORIGIN, Set.of(), 1,
			StoreKind.CAFE, false);
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 90), store(2, StoreKind.PLAY, 75));

		// when
		V2CourseResponse atMinimum = service.recommend("nov", Weekday.FRI, TimeSlot.DINNER, ORIGIN, Set.of(), 1,
			StoreKind.CAFE, false);

		// then
		assertThat(below.stops()).extracting(stop -> stop.place().id()).containsExactly(1L);
		assertThat(below.skipped()).containsExactly(
			new V2CourseResponse.Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"));
		assertThat(atMinimum.stops()).extracting(stop -> stop.place().id()).containsExactly(1L, 2L);
		assertThat(atMinimum.skipped()).isEmpty();
	}

	@ParameterizedTest(name = "{0} 방문 백분위 65인 놀거리 → 3번 자리 선택 {1}")
	@CsvSource({"MON, true", "FRI, true", "SAT, false", "SUN, false"})
	@DisplayName("오후 놀거리(3번) 하한 → 월~금은 60 이라 고르고 주말은 75 라서 건너뛴다")
	void afternoonPlayFloorFollowsWeekday(Weekday weekday, boolean picked) {
		// given
		candidates(TimeSlot.AFTERNOON, store(1, StoreKind.CAFE, 99), store(2, StoreKind.PLAY, 65));

		// when
		V2CourseResponse course = service.recommend("nov", weekday, TimeSlot.AFTERNOON, ORIGIN, Set.of(), 1,
			StoreKind.CAFE, false);

		// then
		assertThat(course.stops()).extracting(stop -> stop.place().id())
			.isEqualTo(picked ? List.of(1L, 2L) : List.of(1L));
		assertThat(course.skipped()).isEqualTo(picked ? List.<V2CourseResponse.Skipped>of()
			: List.of(new V2CourseResponse.Skipped(TimeSlot.AFTERNOON, 3, "NO_NEARBY_MATCH")));
	}
}
