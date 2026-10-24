package com.odyssey.global.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

class ApiEnumsTest {

	@Test
	@DisplayName("응답에 넣는 enum 은 소문자 값이 된다 (LATE_NIGHT → late_night)")
	void toValue() {
		// given
		TimeSlot slot = TimeSlot.LATE_NIGHT;

		// when
		String value = ApiEnums.toValue(slot);

		// then
		assertThat(value).isEqualTo("late_night");
	}

	@Test
	@DisplayName("값이 없는 enum 은 응답에서도 null 이다")
	void toValueNull() {
		// given
		TimeSlot slot = null;

		// when
		String value = ApiEnums.toValue(slot);

		// then
		assertThat(value).isNull();
	}

	@Test
	@DisplayName("요청 값은 같은 이름의 enum 이 된다 (insadong → INSADONG)")
	void parse() {
		// given
		String areaId = "insadong";

		// when
		Optional<Area> area = ApiEnums.parse(Area.class, "areaId", areaId, "all");

		// then
		assertThat(area).contains(Area.INSADONG);
	}

	// 같은 테스트를 areaId 값만 바꿔 4번 실행한다. 값은 JUnit 이 메서드 매개변수로 넣어 준다
	// 결과에는 실행마다 "areaId = [null]" 처럼 name 의 {0} 자리에 값이 찍힌다
	@ParameterizedTest(name = "areaId = [{0}]")
	// null (?areaId 를 안 보냄), "" (?areaId=)
	@NullAndEmptySource
	// " " (공백만 보냄), "all" (전체 선택)
	@ValueSource(strings = {" ", "all"})
	@DisplayName("지역을 생략하거나 all 이면 비어 있어서 지역으로 거르지 않는다")
	void parseWildcard(String areaId) {
		// given: 위 4개 값이 하나씩 areaId 로 들어온다

		// when
		Optional<Area> area = ApiEnums.parse(Area.class, "areaId", areaId, "all");

		// then
		assertThat(area).isEmpty();
	}

	@Test
	@DisplayName("시간대를 생략하면 기본값을 쓴다")
	void parseDefault() {
		// given
		String slot = null;

		// when
		TimeSlot parsed = ApiEnums.parse(TimeSlot.class, "slot", slot, TimeSlot.AFTERNOON);

		// then
		assertThat(parsed).isEqualTo(TimeSlot.AFTERNOON);
	}

	@Test
	@DisplayName("모르는 값이면 INVALID_INPUT 이고 가능한 값 목록을 알려 준다")
	void parseUnknown() {
		// given: 명세서에만 있고 서버에는 없는 값
		String slot = "evening";

		// when
		BusinessException exception = catchThrowableOfType(
			BusinessException.class,
			() -> ApiEnums.parse(TimeSlot.class, "slot", slot, TimeSlot.AFTERNOON));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception).hasMessage("slot 값이 올바르지 않습니다: evening"
			+ " (가능한 값: lunch, afternoon, dinner, late_night)");
	}

	@Test
	@DisplayName("대문자 값은 받지 않는다 (API 값은 소문자만)")
	void parseUppercase() {
		// given
		String areaId = "INSADONG";

		// when
		BusinessException exception = catchThrowableOfType(
			BusinessException.class,
			() -> ApiEnums.parse(Area.class, "areaId", areaId, "all"));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception).hasMessageContaining(
			"(가능한 값: all, jongno, ikseon, insadong, euljiro, myeongdong)");
	}
}
