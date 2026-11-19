package com.odyssey.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.contract.MappingRequests.ContractRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** {@link MappingRequests} 가 WireMock 요청 조건을 대표 요청으로 바꾸는지 확인한다. 서버는 쓰지 않는다. */
class MappingRequestsTest {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private static JsonNode json(String text) {
		return MAPPER.readTree(text);
	}

	private static String uri(String request) {
		return MappingRequests.uri(json(request));
	}

	@Test
	@DisplayName("url 조건 → 그 경로를 그대로 쓴다")
	void urlIsUsedAsIs() {
		// when
		String uri = uri("""
			{"method": "GET", "url": "/api/v2/places/1676"}""");

		// then
		assertThat(uri).isEqualTo("/api/v2/places/1676");
	}

	@Test
	@DisplayName("urlPath 와 equalTo 조건 → 매핑에 적힌 순서대로 쿼리를 붙인다")
	void equalToKeepsMappingOrder() {
		// when
		String uri = uri("""
			{"urlPath": "/api/v2/cells/81607938/visit-pattern",
			 "queryParameters": {"datasetId": {"equalTo": "2025-12-b7786edeeae8"},
			                     "weekday": {"equalTo": "sun"}}}""");

		// then
		assertThat(uri).isEqualTo(
			"/api/v2/cells/81607938/visit-pattern?datasetId=2025-12-b7786edeeae8&weekday=sun");
	}

	@Test
	@DisplayName("urlPath 만 있고 쿼리 조건이 없다 → 물음표 없이 경로만 쓴다")
	void urlPathWithoutQuery() {
		// when
		String uri = uri("""
			{"urlPath": "/api/v2/meta"}""");

		// then
		assertThat(uri).isEqualTo("/api/v2/meta");
	}

	@Test
	@DisplayName("or [absent, equalTo \"\"] → 키를 뺀 요청을 대표로 한다")
	void orWithAbsentOmitsKey() {
		// when
		String uri = uri("""
			{"urlPath": "/api/v2/recommendations",
			 "queryParameters": {"slot": {"equalTo": "dinner"},
			                     "q": {"or": [{"absent": true}, {"equalTo": ""}]},
			                     "kind": {"or": [{"absent": true}, {"equalTo": "all"}]}}}""");

		// then
		assertThat(uri).isEqualTo("/api/v2/recommendations?slot=dinner");
	}

	@Test
	@DisplayName("absent 가 대안 뒤에 있다 → 순서와 상관없이 키를 뺀다")
	void orWithAbsentLastOmitsKey() {
		// when
		String uri = uri("""
			{"urlPath": "/p", "queryParameters":
			 {"category": {"or": [{"equalTo": "food"}, {"absent": true}]}}}""");

		// then
		assertThat(uri).isEqualTo("/p");
	}

	@Test
	@DisplayName("absent 만 있는 조건 → 키를 뺀다")
	void absentOmitsKey() {
		// when
		String uri = uri("""
			{"urlPath": "/p", "queryParameters": {"q": {"absent": true}}}""");

		// then
		assertThat(uri).isEqualTo("/p");
	}

	@Test
	@DisplayName("absent 없는 or → 첫 번째 대안의 값을 쓴다")
	void orWithoutAbsentUsesFirst() {
		// when
		String uri = uri("""
			{"urlPath": "/p", "queryParameters":
			 {"kind": {"or": [{"equalTo": "cafe"}, {"equalTo": "all"}]}}}""");

		// then
		assertThat(uri).isEqualTo("/p?kind=cafe");
	}

	@Test
	@DisplayName("한글과 괄호 · 공백이 있는 값 → 퍼센트 인코딩하고 공백은 %20 이다")
	void encodesKoreanAndSpaces() {
		// when
		String korean = uri("""
			{"urlPath": "/p", "queryParameters": {"q": {"equalTo": "뜰과다원"}}}""");
		String spaced = uri("""
			{"urlPath": "/p", "queryParameters": {"q": {"equalTo": "더컵(THE CUP)"}}}""");

		// then
		assertThat(korean).isEqualTo("/p?q=%EB%9C%B0%EA%B3%BC%EB%8B%A4%EC%9B%90");
		assertThat(spaced).isEqualTo("/p?q=%EB%8D%94%EC%BB%B5%28THE%20CUP%29");
	}

	@Test
	@DisplayName("쉼표로 구분한 셀 ID → 쉼표를 %2C 로 인코딩한다")
	void encodesCommaSeparatedCellIds() {
		// when
		String uri = uri("""
			{"urlPath": "/api/v2/cells/reasons", "queryParameters":
			 {"cellIds": {"equalTo": "81607938,81591938"}}}""");

		// then
		assertThat(uri).isEqualTo("/api/v2/cells/reasons?cellIds=81607938%2C81591938");
	}

	@Test
	@DisplayName("지원하지 않는 매처(matches) → 조용히 넘기지 않고 예외")
	void unsupportedMatcherFails() {
		// when
		IllegalArgumentException exception = catchThrowableOfType(IllegalArgumentException.class,
			() -> uri("""
				{"urlPath": "/p", "queryParameters": {"q": {"matches": "a.*"}}}"""));

		// then
		assertThat(exception).hasMessageContaining("지원하지 않는 쿼리 조건: q");
	}

	@Test
	@DisplayName("url 도 urlPath 도 없다 → 예외")
	void missingPathFails() {
		// when
		IllegalArgumentException exception = catchThrowableOfType(IllegalArgumentException.class,
			() -> uri("""
				{"method": "GET"}"""));

		// then
		assertThat(exception).hasMessageContaining("url 또는 urlPath");
	}

	@Test
	@DisplayName("매핑 파일 → 매핑 하나당 대표 요청 하나를 파일 이름 group 으로 만든다")
	void fromFileMakesOneRequestPerMapping() {
		// given
		JsonNode file = json("""
			{"mappings": [
			  {"request": {"method": "GET", "url": "/api/v2/places/1"}},
			  {"request": {"method": "GET", "url": "/api/v2/places/2"}},
			  {"request": {"method": "GET", "url": "/api/v2/places/3"}}]}""");

		// when
		List<ContractRequest> requests = MappingRequests.fromFile("places", file);

		// then
		assertThat(requests).containsExactly(
			new ContractRequest("places", "/api/v2/places/1"),
			new ContractRequest("places", "/api/v2/places/2"),
			new ContractRequest("places", "/api/v2/places/3"));
	}
}
