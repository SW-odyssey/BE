package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
