package com.odyssey.global.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * 서버 enum 과 API 값 사이의 변환.
 *
 * <p>API 값은 서버 enum 이름을 소문자로 쓴다 (GitHub 이슈 #28).
 * 예) {@code TimeSlot.LATE_NIGHT} ↔ {@code "late_night"},
 * {@code Area.INSADONG} ↔ {@code "insadong"}
 *
 * <p>응답 JSON 의 enum 필드는 Jackson 설정(application.yaml)으로 자동으로 소문자가 된다.
 * 이 클래스는 요청 값을 enum 으로 바꿀 때와, 응답에 enum 을 문자열로 직접 넣을 때 쓴다.
 */
public final class ApiEnums {

	private ApiEnums() {
	}

	/**
	 * enum 을 API 값으로 바꾼다.
	 *
	 * @param value 서버 enum (null 이면 null)
	 * @return 소문자 이름. 예) {@code LATE_NIGHT} → {@code "late_night"}
	 */
	public static String toValue(Enum<?> value) {
		return value == null ? null : value.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * 요청 값을 enum 으로 바꾼다. "전체"를 뜻하는 값이 따로 있는 파라미터에 쓴다.
	 *
	 * <p>사용 예
	 * <pre>{@code
	 * Optional<Area> area = ApiEnums.parse(Area.class, "areaId", query.areaId(), "all");
	 * // areaId 를 안 보냈거나 "all" 이면 Optional.empty() → 지역으로 거르지 않는다
	 * }</pre>
	 *
	 * @param type     바꿀 enum 타입
	 * @param param    파라미터 이름 (오류 문구에 쓴다)
	 * @param value    요청 값. null 이거나 빈 문자열이면 생략한 것으로 본다
	 * @param wildcard "전체"를 뜻하는 값 (예: all, any). 없으면 null
	 * @return 생략했거나 wildcard 면 Optional.empty(), 아니면 해당 enum
	 * @throws BusinessException INVALID_INPUT. 모르는 값이면 허용 값 목록을 문구에 담는다
	 */
	public static <E extends Enum<E>> Optional<E> parse(
		Class<E> type,
		String param,
		String value,
		String wildcard) {

		if (value == null || value.isBlank() || value.equals(wildcard)) {
			return Optional.empty();
		}
		for (E constant : type.getEnumConstants()) {
			if (toValue(constant).equals(value)) {
				return Optional.of(constant);
			}
		}
		throw new BusinessException(
			CommonErrorCode.INVALID_INPUT,
			param + " 값이 올바르지 않습니다: " + value
				+ " (가능한 값: " + allowed(type, wildcard) + ")");
	}

	/**
	 * 요청 값을 enum 으로 바꾼다. "전체" 값이 없고, 생략하면 기본값을 쓰는 파라미터에 쓴다.
	 *
	 * <pre>{@code
	 * TimeSlot slot = ApiEnums.parse(TimeSlot.class, "slot", query.slot(), TimeSlot.AFTERNOON);
	 * }</pre>
	 *
	 * @param type         바꿀 enum 타입
	 * @param param        파라미터 이름 (오류 문구에 쓴다)
	 * @param value        요청 값. null 이거나 빈 문자열이면 생략한 것으로 본다
	 * @param defaultValue 생략했을 때 쓸 값
	 * @return 해당 enum, 생략했으면 defaultValue
	 * @throws BusinessException INVALID_INPUT
	 */
	public static <E extends Enum<E>> E parse(
		Class<E> type,
		String param,
		String value,
		E defaultValue) {

		return parse(type, param, value, (String) null).orElse(defaultValue);
	}

	// 예) all, jongno, ikseon, insadong, euljiro, myeongdong
	private static <E extends Enum<E>> String allowed(Class<E> type, String wildcard) {
		String values = Arrays.stream(type.getEnumConstants())
			.map(ApiEnums::toValue)
			.collect(Collectors.joining(", "));
		return wildcard == null ? values : wildcard + ", " + values;
	}
}
