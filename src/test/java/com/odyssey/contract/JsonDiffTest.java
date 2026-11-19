package com.odyssey.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** {@link JsonDiff} 의 비교 규칙(순서 · 타입 · null · 누락 · 숫자 값)을 하나씩 확인한다. */
class JsonDiffTest {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private static List<String> diff(String expected, String actual) {
		return JsonDiff.diff(MAPPER.readTree(expected), MAPPER.readTree(actual));
	}

	@Test
	@DisplayName("같은 JSON → 차이가 없다")
	void identical() {
		// when & then
		assertThat(diff("""
			{"a": 1, "b": [1, 2], "c": {"d": "x"}}""", """
			{"a": 1, "b": [1, 2], "c": {"d": "x"}}""")).isEmpty();
	}

	@Test
	@DisplayName("객체 키 순서와 공백만 다르다 → 차이가 없다")
	void keyOrderAndWhitespaceIgnored() {
		// when & then
		assertThat(diff("""
			{"a": 1, "b": {"x": true, "y": null}}""", """
			{ "b" : { "y" : null,
			          "x" : true },   "a":1 }""")).isEmpty();
	}

	@Test
	@DisplayName("정수 35 와 실수 35.0 → 값이 같으므로 차이가 없다")
	void integerAndDecimalAreEqual() {
		// when & then
		assertThat(diff("""
			{"distanceMeters": 35}""", """
			{"distanceMeters": 35.0}""")).isEmpty();
	}

	@Test
	@DisplayName("숫자 값이 다르다 → 경로와 두 값을 알려 준다")
	void differentNumber() {
		// when
		List<String> differences = diff("""
			{"data": {"total": 30}}""", """
			{"data": {"total": 29}}""");

		// then
		assertThat(differences).containsExactly("$.data.total: 30 ≠ 29");
	}

	@Test
	@DisplayName("배열 순서가 다르다 → 바뀐 위치마다 경로를 알려 준다")
	void arrayOrderMatters() {
		// when
		List<String> differences = diff("""
			{"items": [{"id": 1}, {"id": 2}]}""", """
			{"items": [{"id": 2}, {"id": 1}]}""");

		// then
		assertThat(differences).containsExactly(
			"$.items[0].id: 1 ≠ 2", "$.items[1].id: 2 ≠ 1");
	}

	@Test
	@DisplayName("배열 길이가 다르다 → 길이 차이를 알려 준다")
	void arrayLength() {
		// when
		List<String> differences = diff("""
			{"bars": [1, 2, 3, 4]}""", """
			{"bars": [1, 2, 3]}""");

		// then
		assertThat(differences).containsExactly("$.bars: 배열 길이 4 ≠ 3");
	}

	@Test
	@DisplayName("값의 타입이 다르다(문자열 \"1\" 대 숫자 1) → 타입 차이다")
	void differentType() {
		// when
		List<String> differences = diff("""
			{"cellId": "81607938"}""", """
			{"cellId": 81607938}""");

		// then
		assertThat(differences).hasSize(1);
		assertThat(differences.getFirst()).startsWith("$.cellId: 타입 string ≠ number");
	}

	@Test
	@DisplayName("null 과 문자열 → 차이다")
	void nullVersusString() {
		// when
		List<String> differences = diff("""
			{"graph": null}""", """
			{"graph": "none"}""");

		// then
		assertThat(differences).hasSize(1);
		assertThat(differences.getFirst()).startsWith("$.graph: 타입 null ≠ string");
	}

	@Test
	@DisplayName("키가 null 이다 대 키가 없다 → 차이다")
	void nullVersusMissingKey() {
		// when
		List<String> differences = diff("""
			{"error": null}""", """
			{}""");

		// then
		assertThat(differences).containsExactly("$.error: 실제 응답에 필드가 없음");
	}

	@Test
	@DisplayName("실제 응답에만 있는 키 → 기대 응답에 없는 필드로 알려 준다")
	void extraKey() {
		// when
		List<String> differences = diff("""
			{"data": {"a": 1}}""", """
			{"data": {"a": 1, "b": 2}}""");

		// then
		assertThat(differences).containsExactly("$.data.b: 기대 응답에 없는 필드");
	}

	@Test
	@DisplayName("깊은 곳의 문자열과 불리언이 다르다 → 전체 경로를 알려 준다")
	void nestedPath() {
		// when
		List<String> differences = diff("""
			{"data": {"items": [{"a": 1}, {"name": "A", "open": true}]}}""", """
			{"data": {"items": [{"a": 1}, {"name": "B", "open": false}]}}""");

		// then
		assertThat(differences).containsExactly(
			"$.data.items[1].name: \"A\" ≠ \"B\"", "$.data.items[1].open: true ≠ false");
	}
}
