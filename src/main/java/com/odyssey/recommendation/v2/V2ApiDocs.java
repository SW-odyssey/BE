package com.odyssey.recommendation.v2;

/**
 * v2 컨트롤러 Swagger 설명에서 여러 API 가 함께 쓰는 문구와 예시 JSON.
 *
 * <p>애너테이션 값은 컴파일 시 상수여야 해서 상수로 둔다. 예시의 오류 문구는 실제 응답과 같아야 하므로
 * 서버 문구를 바꾸면 여기도 고친다.
 */
final class V2ApiDocs {

	static final String DATASET_ID = "배치 id. GET /api/v2/meta 응답의 datasets[].id를 그대로 넣습니다. "
		+ "배치를 새로 올리면 바뀌는 값이라 코드에 적어 두지 마세요.";
	static final String DATASET_ID_EXAMPLE = "2025-12-f79ec9a78f73";

	static final String WEEKDAY = "요일. mon=월, tue=화, wed=수, thu=목, fri=금, sat=토, sun=일. "
		+ "00:00부터 00:59까지는 직전 날의 심야로 보므로 토요일 00:30은 fri 입니다.";
	static final String SLOT = "시간대. lunch=11:00-13:00, afternoon=14:00-17:00, dinner=18:00-21:00, "
		+ "late_night=22:00-01:00(다음 날). 끝 시각은 포함하지 않으며 시간대 사이의 시각에는 추천 데이터가 없습니다.";

	static final String INVALID_INPUT_EXAMPLE = """
		{"success":false,"data":null,"error":{"code":"INVALID_INPUT",
		"message":"weekday 값이 올바르지 않습니다: sunday (가능한 값: mon, tue, wed, thu, fri, sat, sun)"}}
		""";
	static final String UNKNOWN_KEY_EXAMPLE = """
		{"success":false,"data":null,"error":{"code":"INVALID_INPUT",
		"message":"지원하지 않거나 중복된 요청 조건: foo"}}
		""";
	static final String DATASET_NOT_FOUND_EXAMPLE = """
		{"success":false,"data":null,"error":{"code":"DATASET_NOT_FOUND",
		"message":"공개된 분석 배치를 찾을 수 없습니다."}}
		""";
	static final String DATA_UNAVAILABLE_EXAMPLE = """
		{"success":false,"data":null,"error":{"code":"DATA_UNAVAILABLE",
		"message":"데이터를 일시적으로 불러올 수 없습니다."}}
		""";
	static final String INTERNAL_ERROR_EXAMPLE = """
		{"success":false,"data":null,"error":{"code":"INTERNAL_ERROR",
		"message":"서버 오류가 발생했습니다."}}
		""";

	static final String DATASET_NOT_FOUND = "DATASET_NOT_FOUND: 없거나 아직 공개하지 않은 배치. "
		+ "다른 달 배치로 대신 응답하지 않습니다. meta에서 id를 다시 받으세요.";
	static final String DATA_UNAVAILABLE = "DATA_UNAVAILABLE: DB를 읽지 못함(잠시 뒤 다시 시도), 또는 배치를 만든 계산 방식을 "
		+ "지금 서버가 모름(배치와 서버 배포 시점이 어긋난 경우로, BE에 알려야 함)";
	static final String INTERNAL_ERROR = "INTERNAL_ERROR: 서버 내부 오류";

	private V2ApiDocs() {
	}
}
