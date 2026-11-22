package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.recommendation.v2.CourseVisitChecker.Result;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link GooglePlaceHours} 가 Google 응답의 HTTP 상태·본문을 {@link Result} 로 바꾸는지 확인한다.
 * 시각 계산은 {@link WeeklyOpeningHoursTest} 가 본다. Google 대신 이 테스트가 띄운 로컬 서버를 부른다.
 */
class GooglePlaceHoursTest {

	/** 금요일 11~15시 영업 */
	private static final String FRI_LUNCH = """
		{"businessStatus":"OPERATIONAL","regularOpeningHours":{"periods":[
		{"open":{"day":5,"hour":11,"minute":0},"close":{"day":5,"hour":15,"minute":0,"date":{"year":2026}}}]}}""";
	private static final String INVALID_KEY = """
		{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT",
		"details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID",
		"domain":"googleapis.com"}]}}""";

	private HttpServer server;
	private int status;
	private String body;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private Result check(int responseStatus, String responseBody, LocalTime visitTime, int stayMinutes) {
		status = responseStatus;
		body = responseBody;
		var hours = new GooglePlaceHours("key", "http://127.0.0.1:" + server.getAddress().getPort());
		return hours.check("place", Weekday.FRI, visitTime, stayMinutes);
	}

	@ParameterizedTest(name = "{0} 도착 {1}분 → {2}")
	@CsvSource({"12:30, 50, OPEN", "14:20, 50, NOT_OPEN", "16:00, 0, NOT_OPEN"})
	@DisplayName("영업 중이고 영업시간이 있음 → 도착부터 머무는 동안 영업하는지로 판정한다")
	void operationalWithHours(LocalTime visitTime, int stayMinutes, Result expected) {
		// when
		Result result = check(200, FRI_LUNCH, visitTime, stayMinutes);

		// then
		assertThat(result).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
		"폐업 | {\"businessStatus\":\"CLOSED_PERMANENTLY\"} | NOT_OPEN",
		"임시 휴업 | {\"businessStatus\":\"CLOSED_TEMPORARILY\"} | NOT_OPEN",
		"영업시간 없음 | {\"businessStatus\":\"OPERATIONAL\"} | HOURS_UNKNOWN",
		"빈 본문 | {} | NOT_OPEN"})
	@DisplayName("200 응답의 영업 상태 → 폐업·휴업은 영업 아님, 영업시간이 없으면 알 수 없음이다")
	void businessStatus(String name, String responseBody, Result expected) {
		// when
		Result result = check(200, responseBody, LocalTime.of(12, 30), 0);

		// then
		assertThat(result).isEqualTo(expected);
	}

	@Test
	@DisplayName("400 API_KEY_INVALID → 매장 문제가 아니라 확인 불가다")
	void invalidApiKey() {
		// when
		Result result = check(400, INVALID_KEY, LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.UNAVAILABLE);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
		"잘못된 place_id | {\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\"}}",
		"본문이 JSON 이 아님 | not json",
		"다른 reason | {\"error\":{\"details\":[{\"reason\":\"FIELD_INVALID\"}]}}"})
	@DisplayName("API_KEY_INVALID 가 아닌 400 → 그 매장만 영업 아님으로 본다")
	void otherBadRequest(String name, String responseBody) {
		// when
		Result result = check(400, responseBody, LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.NOT_OPEN);
	}

	@Test
	@DisplayName("404 → 없는 매장이라 영업 아님이다")
	void notFound() {
		// when
		Result result = check(404, "{\"error\":{\"code\":404,\"status\":\"NOT_FOUND\"}}", LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.NOT_OPEN);
	}

	@ParameterizedTest(name = "{0} → UNAVAILABLE")
	@CsvSource({"403", "429", "500", "503"})
	@DisplayName("키 권한·한도·Google 장애 → 확인 불가다")
	void serverSideFailure(int responseStatus) {
		// when
		Result result = check(responseStatus, "{\"error\":{}}", LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.UNAVAILABLE);
	}

	@Test
	@DisplayName("API 키가 없음 → 외부를 부르지 않고 확인 불가다")
	void noApiKey() {
		// given
		var hours = new GooglePlaceHours("", "http://127.0.0.1:9");

		// when
		Result result = hours.check("place", Weekday.FRI, LocalTime.NOON, 60);

		// then
		assertThat(result).isEqualTo(Result.UNAVAILABLE);
	}
}
