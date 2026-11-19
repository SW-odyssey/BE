package com.odyssey.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.odyssey.contract.ContractReport.Outcome;
import com.odyssey.contract.MappingRequests.ContractRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * FE WireMock 매핑의 대표 요청을 목서버와 실제 BE 에 똑같이 보내 응답을 비교한다.
 *
 * <p>HTTP 로 보내는 일만 맡는다. 요청을 만드는 일은 {@link MappingRequests}, 비교는
 * {@link JsonDiff}, 출력과 종료 코드는 {@link ContractReport} 가 한다. 기본 test 에는 들어가지 않고
 * {@code ./gradlew contractCheck} 로 실행한다.
 *
 * <p>인자: 매핑 디렉터리, 목서버 주소, BE 주소, (선택) 기대 건수(기본 357). 종료 코드는 통과 0,
 * 실패·누락 1, 인자 오류 2 이다.
 */
public final class ContractReplay {

	static final int DEFAULT_EXPECTED_TOTAL = 357;

	/** 한 서버의 응답. body 가 null 이면 JSON 이 아니다. */
	record Reply(int status, JsonNode body) {

		/** WireMock 이 매핑을 못 찾아 준 기본 404(JSON 이 아니거나 success 키가 없다)인지. */
		boolean isUnmatchedDefault() {
			return status == 404 && (body == null || !body.has("success"));
		}
	}

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private final HttpClient client = HttpClient.newHttpClient();
	private final String mockUrl;
	private final String beUrl;

	ContractReplay(String mockUrl, String beUrl) {
		this.mockUrl = trimSlash(mockUrl);
		this.beUrl = trimSlash(beUrl);
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 3 || args[0].isBlank() || args[1].isBlank() || args[2].isBlank()) {
			System.err.println("사용법: contractCheck -PmappingsDir=<FE mock/v2/mappings> "
				+ "-PmockUrl=<목서버 주소> -PbeUrl=<BE 주소> [-PexpectedTotal=357]");
			System.exit(2);
		}
		int expectedTotal = args.length > 3 && !args[3].isBlank()
			? Integer.parseInt(args[3]) : DEFAULT_EXPECTED_TOTAL;
		List<ContractRequest> requests = readRequests(Path.of(args[0]));
		ContractReplay replay = new ContractReplay(args[1], args[2]);
		List<Outcome> outcomes = new ArrayList<>();
		for (ContractRequest request : requests) {
			outcomes.add(replay.replay(request));
		}
		ContractReport report = new ContractReport(expectedTotal, outcomes);
		System.out.print(report.render());
		System.exit(report.exitCode());
	}

	/** 디렉터리의 매핑 파일을 이름순으로 읽어 파일 이름을 group 으로 하는 요청 목록을 만든다. */
	static List<ContractRequest> readRequests(Path directory) throws IOException {
		List<ContractRequest> requests = new ArrayList<>();
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted()
				.toList()) {
				String name = file.getFileName().toString().replaceFirst("\\.json$", "");
				requests.addAll(MappingRequests.fromFile(name,
					MAPPER.readTree(Files.readString(file))));
			}
		}
		return requests;
	}

	Outcome replay(ContractRequest request) {
		Reply mock;
		try {
			mock = send(mockUrl, request.uri());
		} catch (IOException | RuntimeException exception) {
			return Outcome.failed(request.group(), request.uri(),
				List.of("목서버에 요청하지 못함: " + exception));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return Outcome.failed(request.group(), request.uri(), List.of("중단됨"));
		}
		if (mock.isUnmatchedDefault()) {
			return Outcome.missing(request.group(), request.uri(),
				"목서버가 매핑에 없는 요청으로 처리함(기본 404). 매핑이 로드됐는지 확인");
		}
		Reply be;
		try {
			be = send(beUrl, request.uri());
		} catch (IOException | RuntimeException exception) {
			return Outcome.failed(request.group(), request.uri(),
				List.of("BE 에 요청하지 못함: " + exception));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return Outcome.failed(request.group(), request.uri(), List.of("중단됨"));
		}
		return compare(request, mock, be);
	}

	static Outcome compare(ContractRequest request, Reply mock, Reply be) {
		List<String> differences = new ArrayList<>();
		if (mock.status() != be.status()) {
			differences.add("HTTP 상태 " + mock.status() + " ≠ " + be.status());
		}
		if (mock.body() == null || be.body() == null) {
			differences.add("JSON 이 아닌 응답이 있음 (목서버 " + (mock.body() != null)
				+ ", BE " + (be.body() != null) + ")");
		} else {
			differences.addAll(JsonDiff.diff(mock.body(), be.body()));
		}
		return differences.isEmpty()
			? Outcome.passed(request.group(), request.uri())
			: Outcome.failed(request.group(), request.uri(), differences);
	}

	private Reply send(String baseUrl, String uri) throws IOException, InterruptedException {
		HttpResponse<String> response = client.send(
			HttpRequest.newBuilder(URI.create(baseUrl + uri)).GET().build(),
			HttpResponse.BodyHandlers.ofString());
		return new Reply(response.statusCode(), parse(response.body()));
	}

	private static JsonNode parse(String body) {
		try {
			return MAPPER.readTree(body);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static String trimSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
