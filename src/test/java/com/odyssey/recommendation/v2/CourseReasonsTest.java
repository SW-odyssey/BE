package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseReason.AreaType;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.OpenDuringStay;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.CourseReason.SimilarChoices;
import com.odyssey.recommendation.v2.CourseReasons.StopFacts;

import tools.jackson.databind.json.JsonMapper;

/** {@link CourseReasons} 의 이유별 조건과 순서. */
class CourseReasonsTest {

	/** 모든 이유가 붙는 정류장. 테스트마다 한 값만 바꾼다. */
	private static StopFacts all() {
		return new StopFacts(97, TimeSlot.LUNCH, Optional.of(TimeSlot.LUNCH), Optional.of("weekend_day"),
			"12:30", "13:20", 3, false);
	}

	private static StopFacts with(double percentile, Optional<TimeSlot> peak, Optional<String> area,
		int bandSize, boolean hoursUnknown) {
		return new StopFacts(percentile, TimeSlot.LUNCH, peak, area, "12:30", "13:20", bandSize, hoursUnknown);
	}

	@Test
	@DisplayName("모든 조건이 맞는다 → 다섯 이유를 정해진 순서로 붙인다")
	void allInOrder() {
		// when
		List<CourseReason> reasons = CourseReasons.of(all());

		// then
		assertThat(reasons).containsExactly(new LocalTop(3), new OpenDuringStay("12:30", "13:20"), new PeakNow(),
			new AreaType("weekend_day"), new SimilarChoices(3));
	}

	@Test
	@DisplayName("칸 백분위 99.6 → 상위 1% 다")
	void topPercentAtLeastOne() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(99.6, Optional.of(TimeSlot.LUNCH), Optional.of("mixed"), 3, false));

		// then
		assertThat(reasons.getFirst()).isEqualTo(new LocalTop(1));
	}

	@Test
	@DisplayName("영업시간을 모른다 → OPEN_DURING_STAY 가 없다")
	void noOpenWhenHoursUnknown() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(97, Optional.of(TimeSlot.LUNCH), Optional.of("mixed"), 3, true));

		// then
		assertThat(reasons).noneMatch(OpenDuringStay.class::isInstance);
	}

	@Test
	@DisplayName("가장 붐비는 시간대가 방문 시간대와 다르다 → PEAK_NOW 가 없다")
	void noPeakWhenDifferentSlot() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(97, Optional.of(TimeSlot.DINNER), Optional.of("mixed"), 3, false));

		// then
		assertThat(reasons).noneMatch(PeakNow.class::isInstance);
	}

	@Test
	@DisplayName("가장 붐비는 시간대가 없다 → PEAK_NOW 가 없다")
	void noPeakWhenNoGraph() {
		// when
		List<CourseReason> reasons = CourseReasons.of(with(97, Optional.empty(), Optional.of("mixed"), 3, false));

		// then
		assertThat(reasons).noneMatch(PeakNow.class::isInstance);
	}

	@Test
	@DisplayName("동네 시간 유형이 없다 → AREA_TYPE 이 없다")
	void noAreaTypeWhenMissing() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(97, Optional.of(TimeSlot.LUNCH), Optional.empty(), 3, false));

		// then
		assertThat(reasons).noneMatch(AreaType.class::isInstance);
	}

	@Test
	@DisplayName("범위 후보가 1곳이다 → SIMILAR_CHOICES 가 없다")
	void noSimilarWhenAlone() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(97, Optional.of(TimeSlot.LUNCH), Optional.of("mixed"), 1, false));

		// then
		assertThat(reasons).noneMatch(SimilarChoices.class::isInstance);
	}

	@Test
	@DisplayName("조건이 하나도 맞지 않는다 → LOCAL_TOP 하나만 붙인다")
	void localTopAlways() {
		// when
		List<CourseReason> reasons = CourseReasons.of(with(80, Optional.empty(), Optional.empty(), 1, true));

		// then
		assertThat(reasons).containsExactly(new LocalTop(20));
	}

	@Test
	@DisplayName("JSON 으로 내보낸다 → code 와 그 코드의 값이 한 객체에 담긴다")
	void json() {
		// when
		String json = JsonMapper.builder().build().writerFor(CourseReasonList.class)
			.writeValueAsString(new CourseReasonList(CourseReasons.of(all())));

		// then
		assertThat(json).isEqualTo("""
			{"reasons":[{"code":"LOCAL_TOP","percent":3},\
			{"code":"OPEN_DURING_STAY","from":"12:30","to":"13:20"},\
			{"code":"PEAK_NOW"},\
			{"code":"AREA_TYPE","type":"weekend_day"},\
			{"code":"SIMILAR_CHOICES","count":3}]}""");
	}

	/** 응답처럼 목록의 원소 타입이 {@link CourseReason} 인 자리. */
	record CourseReasonList(List<CourseReason> reasons) {
	}

	@ParameterizedTest
	@CsvSource({"100, 1", "99.5, 1", "98.5, 2", "50, 50", "0, 100"})
	@DisplayName("칸 백분위가 경계값이다 → 100 - 백분위를 반올림하되 1 아래로 내려가지 않는다")
	void topPercentBoundaries(double percentile, int expected) {
		// when
		int percent = CourseReasons.topPercent(percentile);

		// then
		assertThat(percent).isEqualTo(expected);
	}

	@Test
	@DisplayName("범위 후보가 정확히 2곳이다 → SIMILAR_CHOICES 를 붙인다")
	void similarChoicesFromTwo() {
		// when
		List<CourseReason> reasons = CourseReasons.of(
			with(97, Optional.empty(), Optional.empty(), 2, true));

		// then
		assertThat(reasons).containsExactly(new LocalTop(3), new SimilarChoices(2));
	}

	@Test
	@DisplayName("자정을 넘겨 머문다 → OPEN_DURING_STAY 는 도착·나오는 시각을 그대로 담는다")
	void openDuringStayKeepsTimesAcrossMidnight() {
		// given
		StopFacts facts = new StopFacts(97, TimeSlot.LATE_NIGHT, Optional.empty(), Optional.empty(),
			"23:30", "00:40", 1, false);

		// when
		List<CourseReason> reasons = CourseReasons.of(facts);

		// then
		assertThat(reasons).containsExactly(new LocalTop(3), new OpenDuringStay("23:30", "00:40"));
	}

	@Test
	@DisplayName("붐비는 시간대나 동네 유형을 null 로 넘긴다 → 비어 있음(Optional.empty)으로 표현하라고 거절한다")
	void nullOptionalsRejected() {
		// when
		Throwable peakNull = catchThrowable(() -> new StopFacts(97, TimeSlot.LUNCH, null, Optional.empty(),
			"12:30", "13:20", 3, false));
		Throwable areaNull = catchThrowable(() -> new StopFacts(97, TimeSlot.LUNCH, Optional.empty(), null,
			"12:30", "13:20", 3, false));

		// then
		assertThat(peakNull).isInstanceOf(NullPointerException.class).hasMessage("peakSlot");
		assertThat(areaNull).isInstanceOf(NullPointerException.class).hasMessage("areaType");
	}
}
