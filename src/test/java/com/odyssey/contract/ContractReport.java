package com.odyssey.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 대조 결과를 모아 건수와 종료 코드를 정하고 사람이 읽는 문장으로 만든다. 보고 형식을 아는 곳은
 * 여기뿐이다. 네트워크는 쓰지 않는다.
 */
final class ContractReport {

	enum Result {
		/** 상태와 본문이 모두 같다. */
		PASSED,
		/** 응답은 있으나 상태나 본문이 다르거나 BE 가 응답하지 못했다. */
		FAILED,
		/** 목서버가 매핑 응답이 아닌 기본 404 를 줬다. 매핑이 없거나 요청이 매핑과 맞지 않는다. */
		MISSING
	}

	/**
	 * 요청 하나의 결과.
	 *
	 * @param differences 다른 경로와 이유. 통과이면 비어 있다
	 */
	record Outcome(String group, String uri, Result result, List<String> differences) {

		static Outcome passed(String group, String uri) {
			return new Outcome(group, uri, Result.PASSED, List.of());
		}

		static Outcome failed(String group, String uri, List<String> differences) {
			return new Outcome(group, uri, Result.FAILED, differences);
		}

		static Outcome missing(String group, String uri, String reason) {
			return new Outcome(group, uri, Result.MISSING, List.of(reason));
		}
	}

	private final int expectedTotal;
	private final List<Outcome> outcomes;

	/**
	 * @param expectedTotal 대조해야 하는 전체 건수(357). 이보다 적게 읽었어도 실패다
	 */
	ContractReport(int expectedTotal, List<Outcome> outcomes) {
		this.expectedTotal = expectedTotal;
		this.outcomes = List.copyOf(outcomes);
	}

	int total() {
		return outcomes.size();
	}

	int count(Result result) {
		return (int) outcomes.stream().filter(outcome -> outcome.result() == result).count();
	}

	/** @return 기대한 건수를 모두 읽었고 전부 통과했으면 true */
	boolean passed() {
		return total() == expectedTotal && count(Result.PASSED) == total();
	}

	/** @return 통과하면 0, 하나라도 실패·누락이거나 건수가 모자라면 1 */
	int exitCode() {
		return passed() ? 0 : 1;
	}

	String render() {
		StringBuilder text = new StringBuilder();
		Map<String, int[]> byGroup = new LinkedHashMap<>();
		for (Outcome outcome : outcomes) {
			int[] counts = byGroup.computeIfAbsent(outcome.group(), group -> new int[2]);
			counts[1]++;
			if (outcome.result() == Result.PASSED) {
				counts[0]++;
			}
		}
		byGroup.forEach((group, counts) -> text.append(
			String.format("%-16s %d/%d%n", group, counts[0], counts[1])));
		text.append(String.format("합계 %d건 · 성공 %d · 실패 %d · 누락 %d (기대 %d건)%n",
			total(), count(Result.PASSED), count(Result.FAILED), count(Result.MISSING),
			expectedTotal));
		if (total() != expectedTotal) {
			text.append(String.format("건수 불일치: %d건을 읽었고 %d건이어야 합니다.%n",
				total(), expectedTotal));
		}
		for (Outcome outcome : outcomes) {
			if (outcome.result() != Result.PASSED) {
				text.append(String.format("[%s] %s %s%n", outcome.result(), outcome.group(),
					outcome.uri()));
				text.append(outcome.differences().stream()
					.map(difference -> "  - " + difference)
					.collect(Collectors.joining(System.lineSeparator())))
					.append(System.lineSeparator());
			}
		}
		text.append(passed() ? "결과: 통과" : "결과: 실패").append(System.lineSeparator());
		return text.toString();
	}
}
