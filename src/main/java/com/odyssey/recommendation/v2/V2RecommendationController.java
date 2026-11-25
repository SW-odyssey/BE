package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 추천 목록")
public class V2RecommendationController {

	private static final String ALL = "all";
	private static final Set<String> KEYS =
		Set.of("datasetId", "weekday", "slot", "lat", "lng", "category", "kind", "q", "lang");

	private final V2RecommendationService service;

	public V2RecommendationController(V2RecommendationService service) {
		this.service = service;
	}

	/**
	 * 위치 기준 직선 300m 안의 추천 매장을 최대 30곳 준다.
	 *
	 * <p>필수 키가 빠지면 {@code GlobalExceptionHandler} 가 "키 값이 필요합니다." 로 응답하므로
	 * 요청을 record 로 묶지 않고 {@code @RequestParam} 을 유지하고 datasetId 를 맨 앞에 둔다.
	 */
	@GetMapping("/recommendations")
	@Operation(summary = "위치 주변 추천 목록", description = """
		내 위치에서 직선거리 300m 안에 있는 매장 중, 고른 요일·시간대에 사람이 많이 오는 칸의 매장을 최대 30곳 줍니다.

		**고르는 기준**
		- 방문 백분위: 같은 배치·요일·시간대의 모든 칸을 방문량으로 줄 세운 순위입니다(0~100, 높을수록 많이 옴).
		- 방문 백분위 75 이상, 즉 방문량 상위 25% 칸에 있는 매장만 고릅니다.
		- 방문 백분위가 높은 칸의 매장부터, 같으면 가까운 매장부터 나옵니다.
		- 한 칸에 매장이 여러 곳 있으면 모두 따로 들어가고, 30곳 안에 각각 셉니다.
		  같은 칸의 매장은 추천 이유를 공유하므로 FE에서 cellId로 묶어 보여 줄 수 있습니다.

		**거리보다 방문 백분위를 먼저 보는 이유**: 예전에는 80 이상을 한 구간으로 묶고 그 안에서 거리순으로 정렬했습니다.
		그러면 백분위 98인 칸과 81인 칸이 같은 순위가 되어 방문 순위가 사라집니다. 그래서 백분위를 그대로 비교합니다(BE #52, 2026-11-18).
		2025년 11월 기준으로 30곳은 평균 7개 정도의 칸에 나뉘어, 한 칸이 목록을 독차지하는 일은 드뭅니다.
		FE 목 응답(mock/v2/mappings)과 analysis CONTRACT.md의 "80 이상 먼저" 순서는 이전 규칙입니다.

		목록의 cellId를 모아 GET /api/v2/cells/reasons를 한 번 부르면 카드마다 추천 이유를 붙일 수 있습니다.

		**날짜 대신 datasetId를 받는 이유**: 화면 하나를 그리려면 목록 → 이유 → 매장 정보·그래프를 차례로 부릅니다.
		날짜만 받으면 서버가 부를 때마다 배치를 다시 고릅니다. 그 사이에 새 배치가 올라오면 목록은 옛 배치,
		이유는 새 배치로 나와서 서로 맞지 않게 됩니다. 같은 datasetId를 계속 보내면 이런 일이 생기지 않습니다.

		조건에 맞는 매장이 없으면 오류가 아니라 HTTP 200, status=NO_MATCHES, total=0, items=[]입니다.
		""")
	@ApiResponse(responseCode = "200", description = "추천 목록 조회 성공 (NO_MATCHES 포함)",
		useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 필수 값이 없음, 허용 값이 아님(예: weekday=sunday), 잘못된 좌표, "
		+ "category와 kind가 안 맞음, q가 80자를 넘음, 설명에 없는 쿼리 파라미터",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INVALID_INPUT_EXAMPLE)))
	@ApiResponse(responseCode = "404", description = V2ApiDocs.DATASET_NOT_FOUND,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATASET_NOT_FOUND_EXAMPLE)))
	@ApiResponse(responseCode = "503", description = V2ApiDocs.DATA_UNAVAILABLE,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATA_UNAVAILABLE_EXAMPLE)))
	@ApiResponse(responseCode = "500", description = V2ApiDocs.INTERNAL_ERROR,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INTERNAL_ERROR_EXAMPLE)))
	public ApiResult<V2RecommendationResponse> recommend(
		@Parameter(description = V2ApiDocs.DATASET_ID, example = V2ApiDocs.DATASET_ID_EXAMPLE)
		@RequestParam String datasetId,
		@Parameter(description = V2ApiDocs.WEEKDAY, example = "sun", schema = @Schema(
			allowableValues = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"}))
		@RequestParam String weekday,
		@Parameter(description = V2ApiDocs.SLOT, example = "dinner", schema = @Schema(
			allowableValues = {"lunch", "afternoon", "dinner", "late_night"}))
		@RequestParam String slot,
		@Parameter(description = "내 위치의 위도. 일반 지도 좌표(WGS84)입니다.", example = "37.5655")
		@RequestParam double lat,
		@Parameter(description = "내 위치의 경도. 일반 지도 좌표(WGS84)입니다.", example = "126.9905")
		@RequestParam double lng,
		@Parameter(description = "매장 대분류. food=음식점, cafe=카페, shop=쇼핑, play=놀거리, all=전체. 생략하면 food입니다.",
			schema = @Schema(defaultValue = "food", allowableValues = {"food", "cafe", "shop", "play", "all"}))
		@RequestParam(defaultValue = "food") String category,
		@Parameter(description = "매장 종류. korean=한식, bar=술집, other=기타, cafe=카페, shop=쇼핑, play=놀거리, all=전체. "
			+ "category에 속하지 않는 종류를 고르면 400입니다(예: category=cafe&kind=bar).",
			schema = @Schema(defaultValue = ALL,
				allowableValues = {"korean", "bar", "other", "cafe", "shop", "play", ALL}))
		@RequestParam(defaultValue = ALL) String kind,
		@Parameter(description = "매장 이름에 이 글자가 들어간 곳만 고릅니다. 대소문자는 구분하지 않고 앞뒤 공백은 무시합니다. 80자까지.")
		@RequestParam(defaultValue = "") String q,
		@Parameter(description = V2RequestParams.LANGUAGE_DOC, example = "en")
		@RequestParam(required = false) String lang,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		Language language = V2RequestParams.displayLanguage(lang);
		V2RecommendationQuery query = new V2RecommendationQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			V2RequestParams.required(TimeSlot.class, "slot", slot),
			new GeoPoint(lat, lng),
			ApiEnums.parse(Category.class, "category", category, ALL).orElse(null),
			ApiEnums.parse(StoreKind.class, "kind", kind, ALL).orElse(null),
			q);
		V2RecommendationResponse response = service.recommend(query);
		return ApiResult.ok(language == Language.KO ? response : response.withLanguage(language));
	}
}
