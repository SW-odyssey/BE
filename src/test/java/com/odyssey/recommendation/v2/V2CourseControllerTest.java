package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.V2RecommendationSelector.Selected;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

@WebMvcTest(V2CourseController.class)
@Import(V2CourseService.class)
class V2CourseControllerTest {

	private static final GeoPoint ORIGIN = new GeoPoint(37.5655342, 126.9903373);
	private static final LocalTime DINNER_TIME = LocalTime.of(19, 0);

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private CourseCandidates loader;
	@MockitoBean
	private CourseVisitChecker checker;

	@BeforeEach
	void 기본은_모두_영업() {
		given(checker.check(anyString(), any(), any(), anyInt())).willReturn(Result.OPEN);
	}

	private static Selected store(long id, StoreKind kind, double percentile) {
		var place = new Place(id, "매장" + id, 10 + id, kind, 37.566 + id / 10_000.0, 126.992, null, "place-" + id);
		return new Selected(new Candidate(place, "m" + id, percentile), 10 * id);
	}

	private final Map<TimeSlot, List<Candidate>> bySlot = new EnumMap<>(TimeSlot.class);

	/** 시간대의 후보를 넣는다. 출발점·종류는 실제 거르기에 맡기고 여기서는 기록만 한다. */
	private void candidates(TimeSlot slot, GeoPoint origin, StoreKind kind, Selected... ranked) {
		bySlot.put(slot, Arrays.stream(ranked).map(Selected::candidate).toList());
		given(loader.load(eq("nov"), eq(Weekday.FRI), any())).willAnswer(call -> {
			List<TimeSlot> slots = call.getArgument(2);
			return new CandidatePool(new V2Context("nov", "2025-11", Weekday.FRI, slots.getFirst()), bySlot);
		});
	}

	private void dinnerOnly(Selected... ranked) {
		candidates(TimeSlot.DINNER, ORIGIN, StoreKind.KOREAN, ranked);
		for (Selected s : ranked) {
			var at = new GeoPoint(s.candidate().place().lat(), s.candidate().place().lng());
			candidates(TimeSlot.LATE_NIGHT, at, StoreKind.BAR);
		}
	}

	private MockHttpServletRequestBuilder request() {
		return get("/api/v2/course-recommendations").param("datasetId", "nov").param("weekday", "fri")
			.param("startSlot", "dinner").param("lat", "37.5655342").param("lng", "126.9903373");
	}

	private long dinnerPick(long seed) throws Exception {
		String body = mvc.perform(request().param("seed", String.valueOf(seed)))
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.data.stops[0].place.id")).longValue();
	}

	@Test
	void 일위와_3점_이내에서만_seed로_고르고_같은_seed는_같은_결과다() throws Exception {
		dinnerOnly(store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 97.5), store(3, StoreKind.KOREAN, 96.2),
			store(4, StoreKind.KOREAN, 95.9));
		Set<Long> picked = new HashSet<>();
		for (long seed = 1; seed <= 40; seed++) {
			picked.add(dinnerPick(seed));
		}
		assertThat(picked).containsExactlyInAnyOrder(1L, 2L, 3L);
		assertThat(dinnerPick(7)).isEqualTo(dinnerPick(7));
	}

	@Test
	void 점수대가_모두_닫혀_있으면_다음_점수대로_내려가고_그_좌표에서_심야를_찾는다() throws Exception {
		var closed = store(1, StoreKind.KOREAN, 99);
		var next = store(2, StoreKind.KOREAN, 90);
		var bar = store(3, StoreKind.BAR, 98);
		candidates(TimeSlot.DINNER, ORIGIN, StoreKind.KOREAN, closed, next);
		candidates(TimeSlot.LATE_NIGHT, new GeoPoint(next.candidate().place().lat(), next.candidate().place().lng()),
			StoreKind.BAR, bar);
		given(checker.check(eq("place-1"), eq(Weekday.FRI), eq(DINNER_TIME), anyInt())).willReturn(Result.NOT_OPEN);
		mvc.perform(request().param("seed", "3")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETE"))
			.andExpect(jsonPath("$.data.seed").value(3))
			.andExpect(jsonPath("$.data.stops[0].place.id").value(2))
			.andExpect(jsonPath("$.data.stops[0].candidateRank").value(2))
			.andExpect(jsonPath("$.data.stops[0].hoursUnknown").value(false))
			.andExpect(jsonPath("$.data.stops[1].place.id").value(3))
			.andExpect(jsonPath("$.data.stops[1].visitTime").value("22:30"));
	}

	@Test
	void 구간당_영업_확인은_10번까지다() throws Exception {
		given(checker.check(anyString(), any(), any(), anyInt())).willReturn(Result.NOT_OPEN);
		var ranked = new ArrayList<Selected>();
		for (long id = 1; id <= 7; id++) {
			ranked.add(store(id, StoreKind.KOREAN, 99));
		}
		for (long id = 8; id <= 10; id++) {
			ranked.add(store(id, StoreKind.KOREAN, 90));
		}
		for (long id = 11; id <= 12; id++) {
			ranked.add(store(id, StoreKind.KOREAN, 80));
		}
		dinnerOnly(ranked.toArray(Selected[]::new));
		mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.data.stops").isEmpty())
			.andExpect(jsonPath("$.data.interruption.reason").value("NO_VERIFIED_MATCH"));
		verify(checker, atMost(CheckOrder.MAX_CHECKS)).check(anyString(), any(), any(), anyInt());
		verify(checker, never()).check(eq("place-11"), any(), any(), anyInt());
	}

	@Test
	void 범위_안에서_영업하는_곳이_뒤에_있어도_찾는다() throws Exception {
		given(checker.check(anyString(), any(), any(), anyInt())).willReturn(Result.NOT_OPEN);
		given(checker.check(eq("place-7"), any(), any(), anyInt())).willReturn(Result.OPEN);
		var ranked = new ArrayList<Selected>();
		for (long id = 1; id <= 7; id++) {
			ranked.add(store(id, StoreKind.KOREAN, 99));
		}
		dinnerOnly(ranked.toArray(Selected[]::new));
		for (long seed = 1; seed <= 10; seed++) {
			assertThat(dinnerPick(seed)).isEqualTo(7L);
		}
	}

	@Test
	void 외부_조회가_실패하면_다음_후보로_넘어가지_않고_멈춘다() throws Exception {
		given(checker.check(anyString(), any(), any(), anyInt())).willReturn(Result.UNAVAILABLE);
		dinnerOnly(store(1, StoreKind.KOREAN, 99), store(2, StoreKind.KOREAN, 98));
		mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NO_MATCHES"))
			.andExpect(jsonPath("$.data.interruption.reason").value("VERIFICATION_UNAVAILABLE"));
		verify(checker, atMost(1)).check(anyString(), any(), any(), anyInt());
	}

	@Test
	void 오후를_구경으로_고르면_쇼핑_후보에서_찾는다() throws Exception {
		var shop = store(5, StoreKind.SHOP, 97);
		candidates(TimeSlot.AFTERNOON, ORIGIN, StoreKind.SHOP, shop);
		candidates(TimeSlot.DINNER, new GeoPoint(shop.candidate().place().lat(), shop.candidate().place().lng()),
			StoreKind.KOREAN);
		mvc.perform(get("/api/v2/course-recommendations").param("datasetId", "nov").param("weekday", "fri")
				.param("startSlot", "afternoon").param("lat", "37.5655342").param("lng", "126.9903373")
				.param("afternoon", "shop")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stops[0].place.id").value(5))
			.andExpect(jsonPath("$.data.stops[0].place.kind").value("shop"))
			.andExpect(jsonPath("$.data.stops[0].visitTime").value("15:00"));
		mvc.perform(request().param("afternoon", "bar")).andExpect(status().isBadRequest());
	}

	@Test
	void 영업시간이_없는_곳은_쇼핑만_받고_표시한다() throws Exception {
		given(checker.check(anyString(), any(), any(), anyInt())).willReturn(Result.HOURS_UNKNOWN);
		var shop = store(5, StoreKind.SHOP, 97);
		candidates(TimeSlot.AFTERNOON, ORIGIN, StoreKind.SHOP, shop);
		var dinner = store(6, StoreKind.KOREAN, 97);
		candidates(TimeSlot.DINNER, new GeoPoint(shop.candidate().place().lat(), shop.candidate().place().lng()),
			StoreKind.KOREAN, dinner);
		mvc.perform(get("/api/v2/course-recommendations").param("datasetId", "nov").param("weekday", "fri")
				.param("startSlot", "afternoon").param("lat", "37.5655342").param("lng", "126.9903373")
				.param("afternoon", "shop")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stops[0].place.id").value(5))
			.andExpect(jsonPath("$.data.stops[0].hoursUnknown").value(true))
			.andExpect(jsonPath("$.data.stops.length()").value(1))
			.andExpect(jsonPath("$.data.interruption.slot").value("dinner"))
			.andExpect(jsonPath("$.data.interruption.reason").value("NO_VERIFIED_MATCH"));
	}

	@Test
	void 제외한_장소로는_이어가지_않고_부분_코스를_반환한다() throws Exception {
		var dinner = store(1, StoreKind.KOREAN, 99);
		candidates(TimeSlot.DINNER, ORIGIN, StoreKind.KOREAN, dinner);
		candidates(TimeSlot.LATE_NIGHT, new GeoPoint(dinner.candidate().place().lat(), dinner.candidate().place().lng()),
			StoreKind.BAR, store(3, StoreKind.BAR, 99));
		mvc.perform(request().param("excludeIds", "3,4")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PARTIAL")).andExpect(jsonPath("$.data.stops.length()").value(1))
			.andExpect(jsonPath("$.data.interruption.slot").value("late_night"))
			.andExpect(jsonPath("$.data.interruption.reason").value("ALL_EXCLUDED"));
	}

	@Test
	void 첫_후보가_비면_빈_코스와_중단_이유를_반환하고_seed를_정해_준다() throws Exception {
		candidates(TimeSlot.DINNER, ORIGIN, StoreKind.KOREAN);
		mvc.perform(request()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NO_MATCHES"))
			.andExpect(jsonPath("$.data.seed").isNumber())
			.andExpect(jsonPath("$.data.interruption.reason").value("NO_NEARBY_MATCH"));
	}

	@Test
	void 잘못된_좌표와_제외_ID와_seed는_후보를_조회하기_전에_거절한다() throws Exception {
		mvc.perform(request().param("excludeIds", "-1")).andExpect(status().isBadRequest());
		mvc.perform(request().param("seed", "abc")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v2/course-recommendations").param("datasetId", "nov").param("weekday", "fri")
			.param("startSlot", "dinner").param("lat", "91").param("lng", "127")).andExpect(status().isBadRequest());
		verifyNoInteractions(loader);
	}
}
