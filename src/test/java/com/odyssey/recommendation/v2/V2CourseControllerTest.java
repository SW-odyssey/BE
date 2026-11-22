package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.recommendation.v2.CourseReason.AreaType;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.OpenDuringStay;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.CourseReason.SimilarChoices;
import com.odyssey.recommendation.v2.V2CourseResponse.Interruption;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.store.entity.StoreKind;

/** {@link V2CourseController} 의 HTTP 계약. 서비스는 가짜라서 요청 해석·오류 형식·응답 모양만 본다. */
@WebMvcTest(V2CourseController.class)
class V2CourseControllerTest {

	private static final String URL = "/api/v2/course-recommendations";
	private static final V2Context CONTEXT = new V2Context("nov", "2025-11", Weekday.FRI, TimeSlot.DINNER);
	private static final V2CourseResponse PARTIAL = new V2CourseResponse("PARTIAL", CONTEXT, 7,
		List.of(new Stop(3, TimeSlot.DINNER, "19:00", "20:00", 1, 2,
			new Item(12, "백부장집", "81383966", StoreKind.KOREAN, 37.5661, 126.9901, 14.2, "place-12"), false,
			List.of(new LocalTop(3), new OpenDuringStay("19:00", "20:00"), new PeakNow(), new AreaType("mixed"),
				new SimilarChoices(2)))),
		List.of(), new Interruption(TimeSlot.LATE_NIGHT, "NO_VERIFIED_MATCH"));

	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private V2CourseService service;

	@BeforeEach
	void anyCourse() {
		given(service.recommend(any(), any(), any(), any(), anySet(), anyLong(), any())).willReturn(PARTIAL);
	}

	/** 필수 값 다섯 개를 채운 요청에서 key 의 값만 value 로 바꾼다. */
	private static MockHttpServletRequestBuilder validWith(String key, String value) {
		var params = new LinkedMultiValueMap<String, String>();
		params.set("datasetId", "nov");
		params.set("weekday", "fri");
		params.set("startSlot", "dinner");
		params.set("lat", "37.5655342");
		params.set("lng", "126.9903373");
		if (key != null) {
			params.set(key, value);
		}
		return get(URL).params(params);
	}

	private static MockHttpServletRequestBuilder valid() {
		return validWith(null, null);
	}

	private void expectInvalidInput(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.message").value(message));
		then(service).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("모든 값을 보냄 → 바꾼 값 그대로 서비스에 넘긴다")
	void passesParsedValues() throws Exception {
		// given
		var request = valid().param("excludeIds", "3,4").param("seed", "7").param("afternoon", "shop");

		// when
		mvc.perform(request).andExpect(status().isOk());

		// then
		then(service).should().recommend("nov", Weekday.FRI, TimeSlot.DINNER, new GeoPoint(37.5655342, 126.9903373),
			Set.of(3L, 4L), 7L, StoreKind.SHOP);
	}

	@ParameterizedTest(name = "afternoon=\"{0}\" → {1}")
	@CsvSource({"cafe, CAFE", "shop, SHOP", "'', CAFE"})
	@DisplayName("afternoon → cafe·shop 을 종류로 바꾸고 비어 있으면 카페다")
	void afternoonKind(String value, StoreKind expected) throws Exception {
		// given
		var request = validWith("afternoon", value);

		// when
		mvc.perform(request).andExpect(status().isOk());

		// then
		then(service).should().recommend(any(), any(), any(), any(), any(), anyLong(), eq(expected));
	}

	@Test
	@DisplayName("선택 값을 모두 뺌 → 제외 없음·카페이고 seed 는 서버가 정한다")
	void optionalDefaults() throws Exception {
		// given
		var request = valid();

		// when
		mvc.perform(request).andExpect(status().isOk());

		// then
		ArgumentCaptor<Long> seed = ArgumentCaptor.forClass(Long.class);
		then(service).should().recommend(any(), any(), any(), any(), eq(Set.of()), seed.capture(),
			eq(StoreKind.CAFE));
		assertThat(seed.getValue()).isBetween(1L, (1L << 53) - 1);
	}

	@Test
	@DisplayName("코스를 받음 → ApiResult 로 감싸고 enum 은 소문자로 내보낸다")
	void responseShape() throws Exception {
		// when
		ResultActions result = mvc.perform(valid());

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.error").doesNotExist())
			.andExpect(jsonPath("$.data.status").value("PARTIAL"))
			.andExpect(jsonPath("$.data.seed").value(7))
			.andExpect(jsonPath("$.data.context.datasetId").value("nov"))
			.andExpect(jsonPath("$.data.context.weekday").value("fri"))
			.andExpect(jsonPath("$.data.context.slot").value("dinner"))
			.andExpect(jsonPath("$.data.stops[0].position").value(3))
			.andExpect(jsonPath("$.data.skipped").isArray())
			.andExpect(jsonPath("$.data.stops[0].slot").value("dinner"))
			.andExpect(jsonPath("$.data.stops[0].visitTime").value("19:00"))
			.andExpect(jsonPath("$.data.stops[0].candidateRank").value(2))
			.andExpect(jsonPath("$.data.stops[0].hoursUnknown").value(false))
			.andExpect(jsonPath("$.data.stops[0].place.id").value(12))
			.andExpect(jsonPath("$.data.stops[0].place.kind").value("korean"))
			.andExpect(jsonPath("$.data.interruption.slot").value("late_night"))
			.andExpect(jsonPath("$.data.interruption.reason").value("NO_VERIFIED_MATCH"));
	}

	@Test
	@DisplayName("정류장을 받음 → 나오는 시각·도보 분과 code 를 단 이유 목록을 내보낸다")
	void stopTimesAndReasons() throws Exception {
		// when
		ResultActions result = mvc.perform(valid());

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stops[0].leaveTime").value("20:00"))
			.andExpect(jsonPath("$.data.stops[0].walkMinutes").value(1))
			.andExpect(jsonPath("$.data.stops[0].reasons[0].code").value("LOCAL_TOP"))
			.andExpect(jsonPath("$.data.stops[0].reasons[0].percent").value(3))
			.andExpect(jsonPath("$.data.stops[0].reasons[1].code").value("OPEN_DURING_STAY"))
			.andExpect(jsonPath("$.data.stops[0].reasons[1].from").value("19:00"))
			.andExpect(jsonPath("$.data.stops[0].reasons[1].to").value("20:00"))
			.andExpect(jsonPath("$.data.stops[0].reasons[2].code").value("PEAK_NOW"))
			.andExpect(jsonPath("$.data.stops[0].reasons[3].code").value("AREA_TYPE"))
			.andExpect(jsonPath("$.data.stops[0].reasons[3].type").value("mixed"))
			.andExpect(jsonPath("$.data.stops[0].reasons[4].code").value("SIMILAR_CHOICES"))
			.andExpect(jsonPath("$.data.stops[0].reasons[4].count").value(2));
	}

	@ParameterizedTest(name = "excludeIds={0}")
	@CsvSource({"-1", "0", "'3,-2'"})
	@DisplayName("excludeIds 가 양수가 아님 → 400 INVALID_INPUT")
	void nonPositiveExcludeIds(String value) throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("excludeIds", value));

		// then
		expectInvalidInput(result, "excludeIds에는 양의 매장 ID가 필요합니다.");
	}

	@Test
	@DisplayName("excludeIds 가 숫자가 아님 → 400 INVALID_INPUT")
	void nonNumericExcludeIds() throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("excludeIds", "abc"));

		// then
		expectInvalidInput(result, "excludeIds 값이 올바르지 않습니다: abc");
	}

	@Test
	@DisplayName("seed 가 숫자가 아님 → 400 INVALID_INPUT")
	void nonNumericSeed() throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("seed", "abc"));

		// then
		expectInvalidInput(result, "seed 값이 올바르지 않습니다: abc");
	}

	@ParameterizedTest(name = "{0}={1}")
	@CsvSource({"lat, 91", "lat, -90.1", "lng, 181", "lng, NaN"})
	@DisplayName("좌표가 범위 밖이거나 유한하지 않음 → 400 INVALID_INPUT")
	void invalidCoordinates(String key, String value) throws Exception {
		// when
		ResultActions result = mvc.perform(validWith(key, value));

		// then
		expectInvalidInput(result, "유효한 위도·경도가 필요합니다.");
	}

	@ParameterizedTest(name = "afternoon={0}")
	@CsvSource({"bar", "CAFE", "korean"})
	@DisplayName("afternoon 이 cafe·shop 이 아님 → 400 INVALID_INPUT")
	void invalidAfternoon(String value) throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("afternoon", value));

		// then
		expectInvalidInput(result, "afternoon 은 cafe 또는 shop 입니다.");
	}

	@Test
	@DisplayName("startSlot 이 허용 값이 아님 → 400 INVALID_INPUT 과 허용 값 목록")
	void invalidStartSlot() throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("startSlot", "evening"));

		// then
		expectInvalidInput(result,
			"startSlot 값이 올바르지 않습니다: evening (가능한 값: lunch, afternoon, dinner, late_night)");
	}

	@Test
	@DisplayName("datasetId 형식이 아님 → 400 INVALID_INPUT")
	void invalidDatasetId() throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("datasetId", "a b"));

		// then
		expectInvalidInput(result, "meta에서 제공한 datasetId가 필요합니다.");
	}

	@Test
	@DisplayName("받지 않는 키를 보냄 → 400 INVALID_INPUT")
	void unsupportedKey() throws Exception {
		// when
		ResultActions result = mvc.perform(validWith("radiusMeters", "300"));

		// then
		expectInvalidInput(result, "지원하지 않거나 중복된 요청 조건: radiusMeters");
	}

	@Test
	@DisplayName("필수 값 lat 을 뺌 → 400 INVALID_INPUT")
	void missingLat() throws Exception {
		// given
		var request = get(URL).param("datasetId", "nov").param("weekday", "fri").param("startSlot", "dinner")
			.param("lng", "126.9903373");

		// when
		ResultActions result = mvc.perform(request);

		// then
		expectInvalidInput(result, "lat 값이 필요합니다.");
	}

	@Test
	@DisplayName("서비스가 배치를 찾지 못함 → 404 DATASET_NOT_FOUND 를 같은 오류 형식으로 준다")
	void datasetNotFound() throws Exception {
		// given
		given(service.recommend(any(), any(), any(), any(), anySet(), anyLong(), any()))
			.willThrow(new BusinessException(V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));

		// when
		ResultActions result = mvc.perform(valid());

		// then
		result.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("DATASET_NOT_FOUND"))
			.andExpect(jsonPath("$.error.message").value("공개된 분석 배치를 찾을 수 없습니다."));
	}
}
