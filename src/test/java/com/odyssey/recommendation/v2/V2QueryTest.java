package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * {@link V2Query} 의 입력 검증과 정규화를 확인한다. DB 도 스프링도 쓰지 않는 순수 단위 테스트다.
 *
 * <p>잘못된 값은 모두 INVALID_INPUT(400)이어야 한다. 프론트는 이 코드로 입력 오류를 구분한다.
 */
class V2QueryTest {

	private static V2Query query(String dataset, String weekday, String slot, double lat,
		double lng, String category, String kind, String q) {
		return new V2Query(dataset, weekday, slot, lat, lng, category, kind, q);
	}

	private static V2Query withDataset(String dataset) {
		return query(dataset, "mon", "dinner", 37.5, 127.0, "all", "all", "");
	}

	private static V2Query withWeekday(String weekday) {
		return query("nov", weekday, "dinner", 37.5, 127.0, "all", "all", "");
	}

	private static V2Query withSlot(String slot) {
		return query("nov", "mon", slot, 37.5, 127.0, "all", "all", "");
	}

	private static V2Query withCategoryAndKind(String category, String kind) {
		return query("nov", "mon", "dinner", 37.5, 127.0, category, kind, "");
	}

	private static V2Query withCoordinates(double lat, double lng) {
		return query("nov", "mon", "dinner", lat, lng, "all", "all", "");
	}

	private static V2Query withText(String q) {
		return query("nov", "mon", "dinner", 37.5, 127.0, "all", "all", q);
	}

	private static BusinessException catching(ThrowingCallable call) {
		return catchThrowableOfType(BusinessException.class, call);
	}

	// ------------------------------------------------------------ 정규화

	@Test
	@DisplayName("요일 · 시간대 · 카테고리 · 종류를 대문자로 보낸다 → 소문자로 바꿔 담는다")
	void normalizesCase() {
		// given
		String weekday = "MON";

		// when
		V2Query result = query("nov", weekday, "DINNER", 37.5, 127.0, "FOOD", "KOREAN", "");

		// then
		assertThat(result).extracting(V2Query::weekday, V2Query::slot, V2Query::category,
			V2Query::kind).containsExactly("mon", "dinner", "food", "korean");
	}

	@Test
	@DisplayName("카테고리와 종류가 all 이다 → 조건 없음(null)으로 담는다")
	void allMeansNoFilter() {
		// given
		String all = "all";

		// when
		V2Query result = withCategoryAndKind(all, all);

		// then
		assertThat(result).extracting(V2Query::category, V2Query::kind).containsOnlyNulls();
	}

	@Test
	@DisplayName("검색어가 null 이다 → 빈 문자열로 담는다")
	void nullSearchTextBecomesEmpty() {
		// when
		V2Query result = withText(null);

		// then
		assertThat(result.q()).isEmpty();
	}

	@Test
	@DisplayName("검색어 앞뒤에 공백이 있다 → 공백을 뗀 값으로 담는다")
	void searchTextIsStripped() {
		// given
		String text = "  치킨  ";

		// when
		V2Query result = withText(text);

		// then
		assertThat(result.q()).isEqualTo("치킨");
	}

	// ------------------------------------------------------------ 요일 · 시간대

	@ParameterizedTest
	@ValueSource(strings = {"mon_thu", "monday", "", "  ", "evening"})
	@DisplayName("허용하지 않는 요일을 보내면 → INVALID_INPUT")
	void rejectsUnknownWeekday(String weekday) {
		// when
		BusinessException exception = catching(() -> withWeekday(weekday));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@ValueSource(strings = {"evening", "late", "night", "", "  "})
	@DisplayName("허용하지 않는 시간대를 보내면 → INVALID_INPUT")
	void rejectsUnknownSlot(String slot) {
		// when
		BusinessException exception = catching(() -> withSlot(slot));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	// ------------------------------------------------------------ 카테고리 · 종류

	@ParameterizedTest
	@CsvSource({"food,cafe", "cafe,korean", "cafe,bar", "cafe,other"})
	@DisplayName("서로 맞지 않는 카테고리와 종류를 보내면 → INVALID_INPUT")
	void rejectsMismatchedCategoryAndKind(String category, String kind) {
		// when
		BusinessException exception = catching(() -> withCategoryAndKind(category, kind));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@CsvSource({"food,korean", "food,bar", "food,other", "food,all", "cafe,cafe", "cafe,all",
		"all,cafe", "all,korean"})
	@DisplayName("맞는 카테고리와 종류를 보내면 → 그대로 받는다")
	void acceptsMatchingCategoryAndKind(String category, String kind) {
		// when
		V2Query result = withCategoryAndKind(category, kind);

		// then
		assertThat(result).isNotNull();
	}

	// ------------------------------------------------------------ 위치

	@ParameterizedTest
	@CsvSource({"NaN,127", "37.5,NaN", "Infinity,127", "37.5,-Infinity", "90.1,127", "-90.1,127",
		"37.5,180.1", "37.5,-180.1"})
	@DisplayName("유한하지 않거나 범위를 벗어난 위도 · 경도를 보내면 → INVALID_INPUT")
	void rejectsInvalidCoordinates(double lat, double lng) {
		// when
		BusinessException exception = catching(() -> withCoordinates(lat, lng));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@CsvSource({"90,180", "-90,-180", "0,0"})
	@DisplayName("범위 경계의 위도 · 경도를 보내면 → 그대로 받는다")
	void acceptsBoundaryCoordinates(double lat, double lng) {
		// when
		V2Query result = withCoordinates(lat, lng);

		// then
		assertThat(result).extracting(V2Query::lat, V2Query::lng).containsExactly(lat, lng);
	}

	// ------------------------------------------------------------ 검색어

	@Test
	@DisplayName("검색어가 80자다 → 그대로 받는다")
	void acceptsEightyCharacters() {
		// given
		String text = "가".repeat(80);

		// when
		V2Query result = withText(text);

		// then
		assertThat(result.q()).hasSize(80);
	}

	@Test
	@DisplayName("검색어가 81자다 → INVALID_INPUT")
	void rejectsEightyOneCharacters() {
		// given
		String text = "가".repeat(81);

		// when
		BusinessException exception = catching(() -> withText(text));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("공백을 떼면 80자다 → 앞뒤 공백은 길이에 세지 않아 받는다")
	void outerWhitespaceIsNotCounted() {
		// given
		String text = "  " + "가".repeat(80) + "  ";

		// when
		V2Query result = withText(text);

		// then
		assertThat(result.q()).hasSize(80);
	}

	// ------------------------------------------------------------ datasetId

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"a b", "a/b", "한글"})
	@DisplayName("비어 있거나 허용하지 않는 문자가 든 datasetId → INVALID_INPUT")
	void rejectsInvalidDatasetId(String dataset) {
		// when
		BusinessException exception = catching(() -> withDataset(dataset));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("datasetId 가 81자다 → INVALID_INPUT")
	void rejectsTooLongDatasetId() {
		// given
		String dataset = "x".repeat(81);

		// when
		BusinessException exception = catching(() -> withDataset(dataset));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@ValueSource(strings = {"nov", "ds_2026-10", "A-1"})
	@DisplayName("영문 · 숫자 · _ · - 로 된 datasetId → 그대로 받는다")
	void acceptsValidDatasetId(String dataset) {
		// when
		V2Query result = withDataset(dataset);

		// then
		assertThat(result.datasetId()).isEqualTo(dataset);
	}

	@Test
	@DisplayName("datasetId 가 80자다 → 그대로 받는다")
	void acceptsEightyCharacterDatasetId() {
		// given
		String dataset = "x".repeat(80);

		// when
		V2Query result = withDataset(dataset);

		// then
		assertThat(result.datasetId()).hasSize(80);
	}
}
