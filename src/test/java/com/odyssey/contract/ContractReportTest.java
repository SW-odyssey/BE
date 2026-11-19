package com.odyssey.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.contract.ContractReport.Outcome;
import com.odyssey.contract.ContractReport.Result;

/** {@link ContractReport} 의 건수 · 종료 코드 · 출력을 확인한다. */
class ContractReportTest {

	private static Outcome passed(String group, int n) {
		return Outcome.passed(group, "/api/v2/" + group + "/" + n);
	}

	@Test
	@DisplayName("기대한 건수가 모두 통과한다 → 종료 코드 0 과 통과 문구")
	void allPassed() {
		// given
		ContractReport report = new ContractReport(3,
			List.of(passed("places", 1), passed("places", 2), passed("meta", 1)));

		// when & then
		assertThat(report.passed()).isTrue();
		assertThat(report.exitCode()).isZero();
		assertThat(report.render())
			.contains("places           2/2", "meta             1/1")
			.contains("합계 3건 · 성공 3 · 실패 0 · 누락 0 (기대 3건)")
			.contains("결과: 통과");
	}

	@Test
	@DisplayName("실패가 하나 있다 → 종료 코드 1 이고 엔드포인트와 다른 경로를 출력한다")
	void oneFailure() {
		// given
		ContractReport report = new ContractReport(2, List.of(
			passed("places", 1),
			Outcome.failed("places", "/api/v2/places/2", List.of("$.data.name: \"A\" ≠ \"B\""))));

		// when
		String text = report.render();

		// then
		assertThat(report.exitCode()).isEqualTo(1);
		assertThat(report.count(Result.FAILED)).isEqualTo(1);
		assertThat(text).contains("합계 2건 · 성공 1 · 실패 1 · 누락 0")
			.contains("[FAILED] places /api/v2/places/2")
			.contains("  - $.data.name: \"A\" ≠ \"B\"")
			.contains("결과: 실패");
	}

	@Test
	@DisplayName("누락이 하나 있다 → 종료 코드 1 이고 누락 건수로 센다")
	void oneMissing() {
		// given
		ContractReport report = new ContractReport(2, List.of(
			passed("places", 1), Outcome.missing("places", "/api/v2/places/2", "기본 404")));

		// when & then
		assertThat(report.exitCode()).isEqualTo(1);
		assertThat(report.count(Result.MISSING)).isEqualTo(1);
		assertThat(report.render()).contains("누락 1").contains("[MISSING] places");
	}

	@Test
	@DisplayName("전부 통과했지만 기대보다 적게 읽었다 → 종료 코드 1 과 건수 불일치 문구")
	void fewerThanExpected() {
		// given
		ContractReport report = new ContractReport(357, List.of(passed("places", 1)));

		// when & then
		assertThat(report.passed()).isFalse();
		assertThat(report.exitCode()).isEqualTo(1);
		assertThat(report.render()).contains("건수 불일치: 1건을 읽었고 357건이어야 합니다.");
	}
}
