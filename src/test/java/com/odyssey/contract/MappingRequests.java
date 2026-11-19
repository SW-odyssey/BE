package com.odyssey.contract;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import tools.jackson.databind.JsonNode;

/**
 * FE 의 WireMock 매핑 파일에서 대표 요청을 뽑는다. 매핑 JSON 형식을 아는 곳은 여기뿐이다.
 *
 * <p>매핑 하나가 대표 요청 하나다. 지원하는 요청 조건은 {@code url}, {@code urlPath} +
 * {@code queryParameters} 이고, 쿼리 조건은 {@code equalTo}, {@code absent}, {@code or} 이다. 새 조건
 * (예: {@code matches})이 필요하면 {@link #representative} 한 곳만 고친다. 네트워크는 쓰지 않는다.
 */
final class MappingRequests {

	/**
	 * 재생할 요청 하나.
	 *
	 * @param group 매핑 파일 이름(예: places). 결과를 묶어 보여 주는 기준이다
	 * @param uri   경로와 인코딩된 쿼리. 호스트는 재생하는 쪽이 붙인다
	 */
	record ContractRequest(String group, String uri) {
	}

	private MappingRequests() {
	}

	/**
	 * 매핑 파일 하나({@code {"mappings": [...]}})의 모든 매핑을 대표 요청으로 바꾼다.
	 *
	 * @throws IllegalArgumentException 지원하지 않는 요청 조건이 있는 경우
	 */
	static List<ContractRequest> fromFile(String group, JsonNode file) {
		List<ContractRequest> requests = new ArrayList<>();
		for (JsonNode mapping : file.path("mappings")) {
			requests.add(new ContractRequest(group, uri(mapping.path("request"))));
		}
		return requests;
	}

	/** 요청 조건 하나를 만족하는 대표 요청의 경로와 쿼리를 만든다. 쿼리는 매핑에 적힌 순서다. */
	static String uri(JsonNode request) {
		if (request.has("url")) {
			return request.path("url").asText();
		}
		String path = request.path("urlPath").asText();
		if (path.isEmpty()) {
			throw new IllegalArgumentException("url 또는 urlPath 가 필요합니다: " + request);
		}
		StringJoiner query = new StringJoiner("&");
		for (Map.Entry<String, JsonNode> param : request.path("queryParameters").properties()) {
			representative(param.getKey(), param.getValue())
				.ifPresent(value -> query.add(encode(param.getKey()) + "=" + encode(value)));
		}
		return query.length() == 0 ? path : path + "?" + query;
	}

	/**
	 * 쿼리 조건을 만족하는 대표 값을 정한다.
	 *
	 * @return 비어 있으면 그 키를 요청에서 뺀다. {@code or} 에 absent 가 있으면 키를 뺀 요청을 대표로
	 *         삼고, 없으면 첫 번째 대안의 값을 쓴다
	 */
	private static Optional<String> representative(String name, JsonNode matcher) {
		if (matcher.has("equalTo")) {
			return Optional.of(matcher.path("equalTo").asText());
		}
		if (isAbsent(matcher)) {
			return Optional.empty();
		}
		if (matcher.has("or") && matcher.path("or").size() > 0) {
			JsonNode alternatives = matcher.path("or");
			for (JsonNode alternative : alternatives) {
				if (isAbsent(alternative)) {
					return Optional.empty();
				}
			}
			return representative(name, alternatives.get(0));
		}
		throw new IllegalArgumentException("지원하지 않는 쿼리 조건: " + name + " " + matcher);
	}

	private static boolean isAbsent(JsonNode matcher) {
		return matcher.path("absent").asBoolean(false);
	}

	/** 한글과 쉼표, 괄호, 공백을 퍼센트 인코딩한다. 공백은 + 가 아니라 %20 으로 보낸다. */
	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
