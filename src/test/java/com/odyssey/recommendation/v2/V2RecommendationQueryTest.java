package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

/** {@link V2RecommendationQuery} 의 불변식을 확인한다. */
class V2RecommendationQueryTest {

	private static final GeoPoint ORIGIN = new GeoPoint(37.5655, 126.9905);

	private static V2RecommendationQuery query(String datasetId, Category category,
		StoreKind kind, String q) {
		return new V2RecommendationQuery(datasetId, Weekday.MON, TimeSlot.LUNCH, ORIGIN,
			category, kind, q);
	}

	private static void assertInvalid(Runnable action, String message) {
		BusinessException exception = catchThrowableOfType(BusinessException.class, action::run);
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(exception.getMessage()).isEqualTo(message);
	}

	@Test
	@DisplayName("검색어가 null 이다 → 빈 문자열로 담는다")
	void nullSearchTextBecomesEmpty() {
		// when
		V2RecommendationQuery query = query("nov", null, null, null);

		// then
		assertThat(query.q()).isEmpty();
	}

	@Test
	@DisplayName("검색어 앞뒤에 공백이 있다 → 공백을 뗀 값으로 담는다")
	void searchTextIsStripped() {
		// when
		V2RecommendationQuery query = query("nov", null, null, "  스타  ");

		// then
		assertThat(query.q()).isEqualTo("스타");
	}

	@Test
	@DisplayName("검색어가 80자다 → 그대로 받는다")
	void acceptsEightyCharacters() {
		// when
		V2RecommendationQuery query = query("nov", null, null, "가".repeat(80));

		// then
		assertThat(query.q()).hasSize(80);
	}

	@Test
	@DisplayName("검색어가 81자다 → INVALID_INPUT")
	void rejectsEightyOneCharacters() {
		assertInvalid(() -> query("nov", null, null, "가".repeat(81)), "q는 80자까지입니다.");
	}

	@Test
	@DisplayName("공백을 떼면 80자다 → 앞뒤 공백은 길이에 세지 않아 받는다")
	void outerWhitespaceIsNotCounted() {
		// when
		V2RecommendationQuery query = query("nov", null, null, " " + "가".repeat(80) + " ");

		// then
		assertThat(query.q()).hasSize(80);
	}

	@ParameterizedTest
	@CsvSource({"FOOD,CAFE", "CAFE,KOREAN", "CAFE,BAR", "CAFE,OTHER"})
	@DisplayName("서로 맞지 않는 대분류와 가게 분류 → INVALID_INPUT")
	void rejectsMismatchedCategoryAndKind(Category category, StoreKind kind) {
		assertInvalid(() -> query("nov", category, kind, ""),
			"category와 kind가 서로 맞지 않습니다.");
	}

	@ParameterizedTest
	@CsvSource({"FOOD,KOREAN", "FOOD,BAR", "FOOD,OTHER", "CAFE,CAFE"})
	@DisplayName("맞는 대분류와 가게 분류 → 그대로 받는다")
	void acceptsMatchingCategoryAndKind(Category category, StoreKind kind) {
		// when
		V2RecommendationQuery query = query("nov", category, kind, "");

		// then
		assertThat(query.category()).isEqualTo(category);
		assertThat(query.kind()).isEqualTo(kind);
	}

	@Test
	@DisplayName("대분류 또는 가게 분류가 조건 없음(null)이다 → 조합과 상관없이 받는다")
	void acceptsAnyKindWhenOneSideIsUnset() {
		// when & then
		assertThat(query("nov", Category.CAFE, null, "").kind()).isNull();
		assertThat(query("nov", null, StoreKind.CAFE, "").category()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"a b", "a/b", "한글", ""})
	@DisplayName("비어 있거나 허용하지 않는 문자가 든 datasetId → INVALID_INPUT")
	void rejectsInvalidDatasetId(String datasetId) {
		assertInvalid(() -> query(datasetId, null, null, ""),
			"meta에서 제공한 datasetId가 필요합니다.");
	}

	@Test
	@DisplayName("datasetId 가 null 이다 → INVALID_INPUT")
	void rejectsNullDatasetId() {
		assertInvalid(() -> query(null, null, null, ""), "meta에서 제공한 datasetId가 필요합니다.");
	}

	@Test
	@DisplayName("datasetId 가 81자다 → INVALID_INPUT")
	void rejectsTooLongDatasetId() {
		assertInvalid(() -> query("a".repeat(81), null, null, ""),
			"meta에서 제공한 datasetId가 필요합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"nov", "ds_2026-10", "A-1"})
	@DisplayName("영문 · 숫자 · _ · - 로 된 datasetId → 그대로 받는다")
	void acceptsValidDatasetId(String datasetId) {
		// when
		V2RecommendationQuery query = query(datasetId, null, null, "");

		// then
		assertThat(query.datasetId()).isEqualTo(datasetId);
	}

	@Test
	@DisplayName("datasetId 가 80자다 → 그대로 받는다")
	void acceptsEightyCharacterDatasetId() {
		// when
		V2RecommendationQuery query = query("a".repeat(80), null, null, "");

		// then
		assertThat(query.datasetId()).hasSize(80);
	}
}
