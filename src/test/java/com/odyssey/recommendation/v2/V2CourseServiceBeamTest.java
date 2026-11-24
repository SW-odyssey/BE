package com.odyssey.recommendation.v2;

import static com.odyssey.recommendation.v2.BeamSelectorTest.START;
import static com.odyssey.recommendation.v2.BeamSelectorTest.store;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseHoursLookup.Hours;
import com.odyssey.recommendation.v2.CourseReason.AreaType;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.OpenDuringStay;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.CourseReason.SimilarChoices;
import com.odyssey.recommendation.v2.CourseReasonFacts.Facts;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Skipped;
import com.odyssey.store.entity.StoreKind;

/**
 * 운영에 주입되는 구성(영업 확인을 거친 빔 탐색)으로 {@link V2CourseService} 의 응답 계약을 확인한다.
 * {@link V2CourseServiceTest} 는 비교용 선택기로 돌므로, 빔 쪽에서 값이 응답까지 내려가는지는 여기서 고정한다.
 */
class V2CourseServiceBeamTest {

	private final CourseCandidates loader = mock(CourseCandidates.class);
	private final CourseReasonFacts reasonFacts = mock(CourseReasonFacts.class);
	private final Map<TimeSlot, List<Candidate>> bySlot = new EnumMap<>(TimeSlot.class);
	private final Map<String, Hours> hoursByPlace = new HashMap<>();
	private final V2CourseService service = new V2CourseService(loader,
		new CourseSelectionConfig().courseSelector(id -> hoursByPlace.getOrDefault(id, Hours.fixed(Result.OPEN))),
		reasonFacts);

	@BeforeEach
	void pool() {
		given(loader.load(anyString(), any(), any(), anyList())).willAnswer(call -> {
			V2Context context = new V2Context(call.getArgument(0), "2025-11", call.getArgument(1), call.getArgument(2));
			return new CandidatePool(context, bySlot);
		});
		given(reasonFacts.load(anyString(), any(), anyCollection())).willReturn(Facts.NONE);
	}

	private void candidates(TimeSlot slot, Candidate... candidates) {
		bySlot.put(slot, List.of(candidates));
	}

	private void hours(String placeId, Result result) {
		hoursByPlace.put(placeId, Hours.fixed(result));
	}

	private V2CourseResponse recommend(TimeSlot startSlot, Set<Long> excludeIds, StoreKind afternoon,
		boolean lateNight) {
		return service.recommend("nov", Weekday.FRI, startSlot, START, excludeIds, 1, afternoon, lateNight);
	}

	private static List<Long> ids(V2CourseResponse course) {
		return course.stops().stream().map(stop -> stop.place().id()).toList();
	}

	@Test
	@DisplayName("점심 후보가 비슷한 점수대에 둘 → 고른 정류장에 비슷한 후보 수 이유가 붙고 도보 분은 직전 정류장 기준이다")
	void similarChoicesAndWalkMinutesFromPreviousStop() {
		// given: 점심 두 곳은 3점 이내, 오후 카페는 점심에서 약 300m, 저녁은 카페에서 약 400m
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 0, 96), store(4, StoreKind.KOREAN, 20, 95));
		candidates(TimeSlot.AFTERNOON, store(2, StoreKind.CAFE, 300, 99));
		candidates(TimeSlot.DINNER, store(3, StoreKind.KOREAN, 700, 96));
		given(reasonFacts.load(anyString(), any(), anyCollection())).willReturn(
			new Facts(Map.of(1L, TimeSlot.LUNCH, 4L, TimeSlot.LUNCH, 2L, TimeSlot.AFTERNOON), Map.of(3L, "mixed")));

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::position).containsExactly(1, 2, 4);
		assertThat(course.stops().get(0).reasons()).contains(new SimilarChoices(2), new PeakNow());
		assertThat(course.stops().get(1).reasons()).containsExactly(new LocalTop(1),
			new OpenDuringStay("14:00", "15:20"), new PeakNow());
		assertThat(course.stops().get(2).reasons()).containsExactly(new LocalTop(4),
			new OpenDuringStay("18:00", "19:00"), new AreaType("mixed"));
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::walkMinutes).element(1).isEqualTo(6);
		assertThat(course.stops()).extracting(V2CourseResponse.Stop::walkMinutes).element(2).isEqualTo(8);
	}

	@Test
	@DisplayName("카페와 구경을 선택함 → 카페 다음에 쇼핑 장소를 넣고 저녁까지 잇는다")
	void mixedAfternoonIncludesBothKinds() {
		// given
		candidates(TimeSlot.AFTERNOON,
			store(1, StoreKind.CAFE, 0, 96),
			store(2, StoreKind.SHOP, 200, 90),
			store(3, StoreKind.PLAY, 200, 99));
		candidates(TimeSlot.DINNER, store(4, StoreKind.KOREAN, 450, 96));

		// when
		V2CourseResponse course = service.recommend("nov", Weekday.FRI, TimeSlot.AFTERNOON, START,
			Set.of(), 1, CourseSchedule.AfternoonPreference.MIXED, false, WalkPreference.DEFAULT);

		// then
		assertThat(course.stops()).extracting(stop -> stop.place().kind())
			.containsExactly(StoreKind.CAFE, StoreKind.SHOP, StoreKind.KOREAN);
	}

	@Test
	@DisplayName("영업시간이 없는 쇼핑 → hoursUnknown 으로 담고 영업 중 이유는 붙이지 않는다")
	void shopWithUnknownHoursHasNoOpenReason() {
		// given
		candidates(TimeSlot.AFTERNOON, store(1, StoreKind.SHOP, 0, 99));
		hours("p1", Result.HOURS_UNKNOWN);

		// when
		V2CourseResponse course = recommend(TimeSlot.AFTERNOON, Set.of(), StoreKind.SHOP, false);

		// then
		assertThat(course.stops()).singleElement().satisfies(stop -> {
			assertThat(stop.hoursUnknown()).isTrue();
			assertThat(stop.reasons()).containsExactly(new LocalTop(1));
		});
	}

	@Test
	@DisplayName("필수 자리 영업 확인이 실패함 → 앞서 고른 칸만 조회해 부분 코스에 이유를 붙인다")
	void partialCourseAfterRequiredSlotLookupFailure() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 0, 96));
		candidates(TimeSlot.AFTERNOON, store(2, StoreKind.CAFE, 300, 99));
		hours("p2", Result.UNAVAILABLE);
		given(reasonFacts.load("nov", Weekday.FRI, List.of(1L))).willReturn(
			new Facts(Map.of(1L, TimeSlot.LUNCH), Map.of()));

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("PARTIAL");
		assertThat(course.interruption()).isEqualTo(new Interruption(TimeSlot.AFTERNOON, "VERIFICATION_UNAVAILABLE"));
		assertThat(course.stops()).singleElement().satisfies(stop -> assertThat(stop.reasons()).containsExactly(
			new LocalTop(4), new OpenDuringStay("12:00", "12:50"), new PeakNow()));
		then(reasonFacts).should().load("nov", Weekday.FRI, List.of(1L));
	}

	@Test
	@DisplayName("선택 자리(심야) 영업 확인이 실패함 → COMPLETE 이고 건너뛴 자리에 VERIFICATION_UNAVAILABLE 을 남긴다")
	void optionalSlotLookupFailureIsSkipped() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 0, 96));
		candidates(TimeSlot.LATE_NIGHT, store(2, StoreKind.BAR, 300, 99));
		hours("p2", Result.UNAVAILABLE);

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, Set.of(), StoreKind.CAFE, true);

		// then
		assertThat(course.status()).isEqualTo("COMPLETE");
		assertThat(ids(course)).containsExactly(1L);
		assertThat(course.skipped()).containsExactly(new Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"),
			new Skipped(TimeSlot.LATE_NIGHT, 6, "VERIFICATION_UNAVAILABLE"));
	}

	@Test
	@DisplayName("서비스 구역 밖 좌표(후보 0곳) → NO_MATCHES 이고 사유는 NO_NEARBY_MATCH 다")
	void noCandidatesNearOrigin() {
		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "NO_NEARBY_MATCH"));
	}

	@Test
	@DisplayName("첫 자리 후보를 excludeIds 로 모두 제외함 → 사유는 ALL_EXCLUDED 다")
	void allFirstSlotCandidatesExcluded() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 0, 96), store(2, StoreKind.KOREAN, 20, 95));

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, Set.of(1L, 2L), StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "ALL_EXCLUDED"));
	}

	@Test
	@DisplayName("첫 자리 후보가 모두 닫혀 있음 → 사유는 NO_VERIFIED_MATCH 다")
	void allFirstSlotCandidatesClosed() {
		// given
		candidates(TimeSlot.LUNCH, store(1, StoreKind.KOREAN, 0, 96), store(2, StoreKind.KOREAN, 20, 95));
		hours("p1", Result.NOT_OPEN);
		hours("p2", Result.NOT_OPEN);

		// when
		V2CourseResponse course = recommend(TimeSlot.LUNCH, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(course.status()).isEqualTo("NO_MATCHES");
		assertThat(course.interruption()).isEqualTo(new Interruption(TimeSlot.LUNCH, "NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("저녁 놀거리(5번)가 주변에 없음 → NO_NEARBY_MATCH 로 건너뛴다")
	void optionalSlotWithoutNearbyCandidates() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 0, 96));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(ids(course)).containsExactly(1L);
		assertThat(course.skipped()).containsExactly(new Skipped(TimeSlot.DINNER, 5, "NO_NEARBY_MATCH"));
	}

	@Test
	@DisplayName("저녁 놀거리(5번)를 excludeIds 로 제외함 → ALL_EXCLUDED 로 건너뛴다")
	void optionalSlotCandidatesExcluded() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 0, 96), store(2, StoreKind.PLAY, 200, 90));

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, Set.of(2L), StoreKind.CAFE, false);

		// then
		assertThat(ids(course)).containsExactly(1L);
		assertThat(course.skipped()).containsExactly(new Skipped(TimeSlot.DINNER, 5, "ALL_EXCLUDED"));
	}

	@Test
	@DisplayName("저녁 놀거리(5번)가 닫혀 있음 → NO_VERIFIED_MATCH 로 건너뛴다")
	void optionalSlotCandidatesClosed() {
		// given
		candidates(TimeSlot.DINNER, store(1, StoreKind.KOREAN, 0, 96), store(2, StoreKind.PLAY, 200, 90));
		hours("p2", Result.NOT_OPEN);

		// when
		V2CourseResponse course = recommend(TimeSlot.DINNER, Set.of(), StoreKind.CAFE, false);

		// then
		assertThat(ids(course)).containsExactly(1L);
		assertThat(course.skipped()).containsExactly(new Skipped(TimeSlot.DINNER, 5, "NO_VERIFIED_MATCH"));
	}
}
