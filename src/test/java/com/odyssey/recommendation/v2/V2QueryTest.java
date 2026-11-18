package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * {@link V2Query} 의 입력 검증과 정규화를 확인한다. DB 도 스프링도 쓰지 않는 순수 단위 테스트다.
 *
 * <p>잘못된 값은 모두 INVALID_INPUT(400)이어야 한다. 프론트는 이 코드로 입력 오류를 구분한다.
 */
class V2QueryTest {

	private static V2Query query(String dataset, String weekday, String slot, double lat, double lng,
		String category, String kind, String q) {
		return new V2Query(dataset, weekday, slot, lat, lng, category, kind, q);
	}

	private static V2Query valid() {
		return query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", "");
	}

	private static void assertInvalid(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT));
	}

	@Test
	@DisplayName("요일 · 시간대 · 카테고리 · 종류를 대문자로 보낸다 → 소문자로 바꿔 담는다")
	void normalizesCase() {
		// when
		V2Query result = query("nov", "MON", "DINNER", 37.5, 127.0, "FOOD", "KOREAN", "");

		// then
		assertThat(result.weekday()).isEqualTo("mon");
		assertThat(result.slot()).isEqualTo("dinner");
		assertThat(result.category()).isEqualTo("food");
		assertThat(result.kind()).isEqualTo("korean");
	}

	@Test
	@DisplayName("카테고리와 종류가 all 이다 → 조건 없음(null)으로 담는다")
	void allMeansNoFilter() {
		// when
		V2Query result = query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", "");

		// then
		assertThat(result.category()).isNull();
		assertThat(result.kind()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"mon_thu", "monday", "", "  ", "evening"})
	@DisplayName("허용하지 않는 요일 → INVALID_INPUT")
	void rejectsUnknownWeekday(String weekday) {
		assertInvalid(() -> query("nov", weekday, "dinner", 37.5, 127.0, "all", "all", ""));
	}

	@ParameterizedTest
	@ValueSource(strings = {"evening", "late", "night", "", "  "})
	@DisplayName("허용하지 않는 시간대 → INVALID_INPUT")
	void rejectsUnknownSlot(String slot) {
		assertInvalid(() -> query("nov", "mon", slot, 37.5, 127.0, "all", "all", ""));
	}

	@ParameterizedTest
	@CsvSource({"food,cafe", "cafe,korean", "cafe,bar", "cafe,other"})
	@DisplayName("서로 맞지 않는 카테고리와 종류 → INVALID_INPUT")
	void rejectsMismatchedCategoryAndKind(String category, String kind) {
		assertInvalid(() -> query("nov", "mon", "dinner", 37.5, 127.0, category, kind, ""));
	}

	@ParameterizedTest
	@CsvSource({"food,korean", "food,bar", "food,other", "food,all", "cafe,cafe", "cafe,all",
		"all,cafe", "all,korean"})
	@DisplayName("맞는 카테고리와 종류 → 통과한다")
	void acceptsMatchingCategoryAndKind(String category, String kind) {
		assertThatCode(() -> query("nov", "mon", "dinner", 37.5, 127.0, category, kind, ""))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource({"NaN,127", "37.5,NaN", "Infinity,127", "37.5,-Infinity", "90.1,127", "-90.1,127",
		"37.5,180.1", "37.5,-180.1"})
	@DisplayName("유한하지 않거나 범위를 벗어난 위도 · 경도 → INVALID_INPUT")
	void rejectsInvalidCoordinates(double lat, double lng) {
		assertInvalid(() -> query("nov", "mon", "dinner", lat, lng, "all", "all", ""));
	}

	@ParameterizedTest
	@CsvSource({"90,180", "-90,-180", "0,0"})
	@DisplayName("범위 경계의 위도 · 경도 → 통과한다")
	void acceptsBoundaryCoordinates(double lat, double lng) {
		assertThatCode(() -> query("nov", "mon", "dinner", lat, lng, "all", "all", ""))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("검색어가 null 이거나 앞뒤 공백이 있다 → 빈 문자열 / 공백을 뗀 값으로 담는다")
	void normalizesSearchText() {
		// when / then
		assertThat(query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", null).q()).isEmpty();
		assertThat(query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", "  치킨  ").q())
			.isEqualTo("치킨");
	}

	@Test
	@DisplayName("검색어가 80자다 → 통과하고, 81자다 → INVALID_INPUT")
	void searchTextLengthLimit() {
		// when / then
		assertThat(query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", "가".repeat(80)).q())
			.hasSize(80);
		assertInvalid(() -> query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", "가".repeat(81)));
	}

	@Test
	@DisplayName("공백을 뗀 뒤 80자다 → 앞뒤 공백은 길이에 세지 않는다")
	void searchTextLengthIgnoresOuterWhitespace() {
		// when
		V2Query result = query("nov", "mon", "dinner", 37.5, 127.0, "all", "all",
			"  " + "가".repeat(80) + "  ");

		// then
		assertThat(result.q()).hasSize(80);
	}

	@Test
	@DisplayName("datasetId 가 null · 빈 값 · 허용하지 않는 문자 · 81자 → INVALID_INPUT")
	void rejectsInvalidDatasetId() {
		assertInvalid(() -> query(null, "mon", "dinner", 37.5, 127.0, "all", "all", ""));
		assertInvalid(() -> query("", "mon", "dinner", 37.5, 127.0, "all", "all", ""));
		assertInvalid(() -> query("a b", "mon", "dinner", 37.5, 127.0, "all", "all", ""));
		assertInvalid(() -> query("a/b", "mon", "dinner", 37.5, 127.0, "all", "all", ""));
		assertInvalid(() -> query("한글", "mon", "dinner", 37.5, 127.0, "all", "all", ""));
		assertInvalid(() -> query("x".repeat(81), "mon", "dinner", 37.5, 127.0, "all", "all", ""));
	}

	@Test
	@DisplayName("영문 · 숫자 · _ · - 로 된 datasetId → 통과한다 (1~80자)")
	void acceptsValidDatasetId() {
		assertThatCode(() -> query("ds_2026-10", "mon", "dinner", 37.5, 127.0, "all", "all", ""))
			.doesNotThrowAnyException();
		assertThatCode(() -> query("x".repeat(80), "mon", "dinner", 37.5, 127.0, "all", "all", ""))
			.doesNotThrowAnyException();
		assertThat(valid().datasetId()).isEqualTo("nov");
	}
}
