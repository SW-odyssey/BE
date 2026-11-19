package com.odyssey.cell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** {@link GeoPoint} 의 좌표 검증과 거리 계산을 확인한다. */
class GeoPointTest {

	@ParameterizedTest
	@CsvSource({"NaN,127", "37.5,NaN", "Infinity,127", "37.5,-Infinity", "90.1,127", "-90.1,127",
		"37.5,180.1", "37.5,-180.1"})
	@DisplayName("유한하지 않거나 범위를 벗어난 위도 · 경도 → INVALID_INPUT")
	void rejectsInvalidCoordinates(double lat, double lng) {
		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> new GeoPoint(lat, lng));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception.getMessage()).isEqualTo("유효한 위도·경도가 필요합니다.");
	}

	@ParameterizedTest
	@CsvSource({"90,180", "-90,-180", "0,0"})
	@DisplayName("범위 경계의 위도 · 경도 → 그대로 받는다")
	void acceptsBoundaryCoordinates(double lat, double lng) {
		// when
		GeoPoint point = new GeoPoint(lat, lng);

		// then
		assertThat(point.lat()).isEqualTo(lat);
		assertThat(point.lng()).isEqualTo(lng);
	}

	@Test
	@DisplayName("같은 지점 → 거리는 0m 다")
	void samePointDistance() {
		// given
		GeoPoint point = new GeoPoint(37.5655, 126.9905);

		// when & then
		assertThat(point.distanceTo(point)).isZero();
	}

	@Test
	@DisplayName("위도가 1도 차이 난다 → 약 111.19km 다")
	void oneDegreeOfLatitude() {
		// given
		GeoPoint from = new GeoPoint(37.0, 127.0);
		GeoPoint to = new GeoPoint(38.0, 127.0);

		// when & then
		assertThat(from.distanceTo(to)).isCloseTo(111_194.9, within(1.0));
	}

	@Test
	@DisplayName("두 점의 순서를 바꾼다 → 거리는 같다")
	void distanceIsSymmetric() {
		// given
		GeoPoint a = new GeoPoint(37.5655, 126.9905);
		GeoPoint b = new GeoPoint(37.5700, 126.9800);

		// when & then
		assertThat(a.distanceTo(b)).isEqualTo(b.distanceTo(a));
	}
}
