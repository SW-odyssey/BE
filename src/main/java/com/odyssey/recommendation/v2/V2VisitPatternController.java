package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 주변 방문 패턴")
public class V2VisitPatternController {

	private static final Set<String> KEYS = Set.of("datasetId", "weekday");

	private final V2VisitPatternService service;

	public V2VisitPatternController(V2VisitPatternService service) {
		this.service = service;
	}

	/** 같은 칸·같은 요일의 시간대별 방문 패턴. 시간대(slot)는 받지 않는다. */
	@GetMapping("/cells/{cellId}/visit-pattern")
	@Operation(summary = "칸의 요일별 방문 패턴", description = """
		매장 상세 화면의 막대그래프용입니다. 이 칸에 그 요일 시간대별로 사람이 얼마나 오는지 보여 줍니다.
		시간대(slot)는 받지 않습니다. 목록에서 어떤 시간대를 골랐든 네 시간대를 모두 보여 주기 때문입니다.
		datasetId·weekday에는 추천 목록을 부를 때 보낸 값을 그대로 넣습니다.

		graph.bars에 점심 → 오후 → 저녁 → 심야 순서로 막대 4개가 옵니다.
		가장 많이 오는 시간대를 100으로 두고 나머지를 그 비율로 나타낸 값입니다(relativeHeight, 정수 0~100).
		5 단위로 반올림하고, 방문이 조금이라도 있으면 최소 1로 둡니다. 실제 인원이나 매장 점수가 아닙니다.
		다른 칸과 비교하는 값이 아니고, 네 값을 더해 100이 될 필요도 없습니다.

		| status | graph |
		|---|---|
		| AVAILABLE | 막대 4개, 100이 하나. 그 시간대가 graph.highlightSlot |
		| NO_DISTINCT_PEAK | 막대 4개. 1위가 2위의 1.25배에 못 미쳐 100이 여러 개일 수 있고 highlightSlot=null |
		| INCOMPLETE_TIME_SLOTS | 자료가 빠진 시간대는 relativeHeight=null, highlightSlot=null. 쓸 수 있는 시간대가 없으면 graph=null |
		| NO_POSITIVE_VISITS | graph=null |

		relativeHeight=null인 막대는 0으로 그리지 말고 "자료 없음"으로 표시하세요. 0으로 그리면 "아무도 안 온다"로 잘못 보입니다.
		""")
	@ApiResponse(responseCode = "200", description = "방문 패턴 조회 성공 (graph=null 포함)",
		useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 필수 값이 없음, 허용 값이 아님(예: weekday=sunday), "
		+ "0 이하의 cellId, 설명에 없는 쿼리 파라미터(slot을 보내도 400)",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INVALID_INPUT_EXAMPLE)))
	@ApiResponse(responseCode = "404", description = "DATASET_NOT_FOUND: 없거나 아직 공개하지 않은 배치 / "
		+ "CELL_NOT_FOUND: 이 배치에 이 칸·요일 자료가 없음",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = {
				@ExampleObject(name = "DATASET_NOT_FOUND", value = V2ApiDocs.DATASET_NOT_FOUND_EXAMPLE),
				@ExampleObject(name = "CELL_NOT_FOUND", value = """
					{"success":false,"data":null,"error":{"code":"CELL_NOT_FOUND",
					"message":"해당 배치에 셀 자료가 없습니다."}}
					""")}))
	@ApiResponse(responseCode = "503", description = V2ApiDocs.DATA_UNAVAILABLE,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATA_UNAVAILABLE_EXAMPLE)))
	@ApiResponse(responseCode = "500", description = V2ApiDocs.INTERNAL_ERROR,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INTERNAL_ERROR_EXAMPLE)))
	public ApiResult<V2VisitPatternResponse> visitPattern(
		@Parameter(description = "칸 번호. 추천 목록의 items[].cellId입니다.", example = "81591941")
		@PathVariable long cellId,
		@Parameter(description = V2ApiDocs.DATASET_ID, example = V2ApiDocs.DATASET_ID_EXAMPLE)
		@RequestParam String datasetId,
		@Parameter(description = V2ApiDocs.WEEKDAY, example = "sun", schema = @Schema(
			allowableValues = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"}))
		@RequestParam String weekday,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		return ApiResult.ok(service.visitPattern(new V2VisitPatternQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			cellId)));
	}
}
