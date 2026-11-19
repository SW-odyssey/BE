package com.odyssey.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * 두 JSON 의 차이를 경로별로 모은다. 비교 규칙은 여기에만 있다. 네트워크는 쓰지 않는다.
 *
 * <ul>
 *   <li>객체의 키 순서와 공백은 무시한다.
 *   <li>키 누락, null, 값, 타입의 차이는 모두 차이로 센다. null 과 키 없음은 다르다.
 *   <li>배열은 길이와 순서를 그대로 비교한다.
 *   <li>숫자는 값으로 비교한다. JSON 에는 숫자 타입이 하나뿐이라 35 와 35.0 은 같다.
 *   <li>타입은 string, number, boolean, null, object, array 여섯 가지로 나눈다.
 * </ul>
 */
final class JsonDiff {

	private JsonDiff() {
	}

	/**
	 * @param expected 기대하는 JSON(목서버 응답)
	 * @param actual   실제 JSON(BE 응답)
	 * @return 차이가 있는 경로와 이유. 같으면 비어 있다. 경로는 {@code $.data.items[0].name} 형식이다
	 */
	static List<String> diff(JsonNode expected, JsonNode actual) {
		List<String> differences = new ArrayList<>();
		compare("$", expected, actual, differences);
		return differences;
	}

	private static void compare(String path, JsonNode expected, JsonNode actual,
		List<String> differences) {
		String expectedType = typeOf(expected);
		String actualType = typeOf(actual);
		if (!expectedType.equals(actualType)) {
			differences.add(path + ": 타입 " + expectedType + " ≠ " + actualType
				+ " (" + expected + " ≠ " + actual + ")");
			return;
		}
		switch (expectedType) {
			case "object" -> compareObjects(path, expected, actual, differences);
			case "array" -> compareArrays(path, expected, actual, differences);
			case "number" -> {
				if (expected.decimalValue().compareTo(actual.decimalValue()) != 0) {
					differences.add(path + ": " + expected + " ≠ " + actual);
				}
			}
			default -> {
				if (!expected.equals(actual)) {
					differences.add(path + ": " + expected + " ≠ " + actual);
				}
			}
		}
	}

	private static void compareObjects(String path, JsonNode expected, JsonNode actual,
		List<String> differences) {
		for (Map.Entry<String, JsonNode> field : expected.properties()) {
			String childPath = path + "." + field.getKey();
			JsonNode actualChild = actual.get(field.getKey());
			if (actualChild == null) {
				differences.add(childPath + ": 실제 응답에 필드가 없음");
			} else {
				compare(childPath, field.getValue(), actualChild, differences);
			}
		}
		for (Map.Entry<String, JsonNode> field : actual.properties()) {
			if (!expected.has(field.getKey())) {
				differences.add(path + "." + field.getKey() + ": 기대 응답에 없는 필드");
			}
		}
	}

	private static void compareArrays(String path, JsonNode expected, JsonNode actual,
		List<String> differences) {
		if (expected.size() != actual.size()) {
			differences.add(path + ": 배열 길이 " + expected.size() + " ≠ " + actual.size());
		}
		int common = Math.min(expected.size(), actual.size());
		for (int i = 0; i < common; i++) {
			compare(path + "[" + i + "]", expected.get(i), actual.get(i), differences);
		}
	}

	private static String typeOf(JsonNode node) {
		if (node.isObject()) {
			return "object";
		}
		if (node.isArray()) {
			return "array";
		}
		if (node.isNumber()) {
			return "number";
		}
		if (node.isBoolean()) {
			return "boolean";
		}
		if (node.isNull()) {
			return "null";
		}
		return "string";
	}
}
