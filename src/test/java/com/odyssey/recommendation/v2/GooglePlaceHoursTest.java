package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.odyssey.recommendation.v2.GooglePlaceHours.Period;
import com.odyssey.recommendation.v2.GooglePlaceHours.Point;

class GooglePlaceHoursTest {

	private static final int SUN = 0, FRI = 5, SAT = 6;

	private static Period period(int openDay, int openHour, int closeDay, int closeHour) {
		return new Period(new Point(openDay, openHour, 0), new Point(closeDay, closeHour, 0));
	}

	@Test
	void 영업시간_안이면_열려_있고_마감_정각은_밖이다() {
		var lunch = List.of(period(FRI, 11, FRI, 15));
		assertThat(GooglePlaceHours.isOpen(lunch, Weekday.FRI, LocalTime.of(12, 30), 0)).isTrue();
		assertThat(GooglePlaceHours.isOpen(lunch, Weekday.FRI, LocalTime.of(15, 0), 0)).isFalse();
		assertThat(GooglePlaceHours.isOpen(lunch, Weekday.THU, LocalTime.of(12, 30), 0)).isFalse();
	}

	@Test
	void 자정과_토요일에서_일요일로_넘어가는_구간을_처리한다() {
		var overnight = List.of(period(FRI, 18, SAT, 2), period(SAT, 18, SUN, 2));
		assertThat(GooglePlaceHours.isOpen(overnight, Weekday.FRI, LocalTime.of(23, 30), 0)).isTrue();
		assertThat(GooglePlaceHours.isOpen(overnight, Weekday.SAT, LocalTime.of(1, 0), 0)).isTrue();
		assertThat(GooglePlaceHours.isOpen(overnight, Weekday.SUN, LocalTime.of(1, 0), 0)).isTrue();
		assertThat(GooglePlaceHours.isOpen(overnight, Weekday.SUN, LocalTime.of(23, 30), 0)).isFalse();
	}

	@Test
	void 머무는_동안_문을_닫으면_영업이_아니고_마감_정각에_나오는_것은_된다() {
		var lunch = List.of(period(FRI, 11, FRI, 15));
		assertThat(GooglePlaceHours.isOpen(lunch, Weekday.FRI, LocalTime.of(14, 10), 50)).isTrue();
		assertThat(GooglePlaceHours.isOpen(lunch, Weekday.FRI, LocalTime.of(14, 20), 50)).isFalse();
		var bar = List.of(period(SAT, 18, SUN, 0), period(FRI, 18, SAT, 1));
		assertThat(GooglePlaceHours.isOpen(bar, Weekday.SAT, LocalTime.of(23, 30), 70)).isFalse();
		assertThat(GooglePlaceHours.isOpen(bar, Weekday.FRI, LocalTime.of(23, 30), 70)).isTrue();
	}

	@Test
	void 마감이_없으면_24시간_영업이다() {
		var always = List.of(new Period(new Point(SUN, 0, 0), null));
		assertThat(GooglePlaceHours.isOpen(always, Weekday.WED, LocalTime.of(23, 30), 0)).isTrue();
		assertThat(GooglePlaceHours.isOpen(null, Weekday.WED, LocalTime.of(12, 30), 0)).isFalse();
	}

	@Test
	void 키가_없으면_외부를_부르지_않고_확인_불가다() {
		var hours = new GooglePlaceHours("", "http://127.0.0.1:9");
		assertThat(hours.check("place", Weekday.FRI, LocalTime.NOON, 60)).isEqualTo(Result.UNAVAILABLE);
	}
}
