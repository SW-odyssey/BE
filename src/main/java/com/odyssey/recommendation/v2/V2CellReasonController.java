package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 셀 추천 이유")
public class V2CellReasonController {

	private static final Set<String> KEYS = Set.of("datasetId", "weekday", "slot", "cellIds");

	private final V2CellReasonService service;

	public V2CellReasonController(V2CellReasonService service) {
		this.service = service;
	}

	/**
	 * 목록에 나온 칸들의 추천 이유를 한 번에 준다. cellIds 는 쉼표로 구분한 중복 없는 양의 정수
	 * 1~30개다. 필수 키 누락 문구를 지키려고 {@code @RequestParam} 을 유지한다.
	 */
	@GetMapping("/cells/reasons")
	@Operation(summary = "칸별 추천 이유 일괄 조회", description = """
		추천 목록에 나온 칸들의 추천 이유를 한 번에 줍니다. 카드마다 한 번씩 부르지 않아도 되게 만든 API입니다.
		datasetId·weekday·slot에는 추천 목록을 부를 때 보낸 값을 그대로 넣습니다.
		응답의 reasonsByCellId는 칸 번호를 키로 한 객체이고, 키는 요청한 cellIds 순서대로 옵니다.
		JSON 객체 키라서 칸 번호가 문자열("81591941")로 옵니다.

		**칸마다 오는 status**
		- AVAILABLE: reasons에 이유 코드가 1~2개, 화면에 보여 줄 순서대로 옵니다.
		- NO_RECOMMENDATION_EVIDENCE: 자료 품질은 통과했지만 추천 근거 기준에 못 미칩니다. reasons=[]
		- CELL_NOT_IN_DATASET: 이 배치에 요청한 칸이 없습니다. reasons=[]
		- NOT_OBSERVED: 이 조건의 자료가 없습니다.
		- INCOMPLETE: 필요한 연령·성별 관측이 불완전합니다.
		- NO_POSITIVE_VISITS: 관측된 방문이 없습니다.

		**이유 코드** (서버는 코드만 주고, 화면 문구와 언어별 라벨은 FE가 만듭니다)
		- VISIT_RELATIVE_HIGH: 같은 달·요일·시간대의 다른 칸보다 방문이 많음
		- DAY_DISTINCT: 같은 시간대의 다른 요일(월~목)과 비교한 특징
		- TIME_PEAK: 같은 요일의 다른 시간대와 비교한 특징
		- STAY_DISTINCT: 방문 순위가 비슷한 다른 칸과 비교한 체류 특징
		- WALK_DISTINCT: 방문 순위가 비슷한 다른 칸과 비교한 보행 특징
		- AGE20_DISTINCT · AGE30_DISTINCT: 20·30대 방문자 안에서 20대 또는 30대가 두드러짐

		문구를 만들 때 주의할 점:
		- 비교 대상은 내 주변 300m 안의 칸만이 아니라 전체 칸입니다. "가까운 곳 중에서"라고 쓰지 않습니다.
		- 연령 코드는 매장의 실제 손님 연령 비율이 아닙니다.

		코드 뜻의 출처는 analysis 저장소 docs/recommendation-v2/CONTRACT.md입니다.
		""")
	@ApiResponse(responseCode = "200", description = "이유 조회 성공 (자료 없는 칸도 200)",
		useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 필수 값이 없음, 허용 값이 아님(예: weekday=sunday), "
		+ "cellIds가 1~30개가 아니거나 중복·0 이하 번호가 있음, 설명에 없는 쿼리 파라미터",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = """
				{"success":false,"data":null,"error":{"code":"INVALID_INPUT",
				"message":"cellIds는 중복 없는 양의 정수 1~30개입니다."}}
				""")))
	@ApiResponse(responseCode = "404", description = V2ApiDocs.DATASET_NOT_FOUND,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATASET_NOT_FOUND_EXAMPLE)))
	@ApiResponse(responseCode = "503", description = V2ApiDocs.DATA_UNAVAILABLE,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATA_UNAVAILABLE_EXAMPLE)))
	@ApiResponse(responseCode = "500", description = V2ApiDocs.INTERNAL_ERROR,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INTERNAL_ERROR_EXAMPLE)))
	public ApiResult<V2ReasonsResponse> reasons(
		@Parameter(description = V2ApiDocs.DATASET_ID, example = V2ApiDocs.DATASET_ID_EXAMPLE)
		@RequestParam String datasetId,
		@Parameter(description = V2ApiDocs.WEEKDAY, example = "sun", schema = @Schema(
			allowableValues = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"}))
		@RequestParam String weekday,
		@Parameter(description = V2ApiDocs.SLOT, example = "dinner", schema = @Schema(
			allowableValues = {"lunch", "afternoon", "dinner", "late_night"}))
		@RequestParam String slot,
		@Parameter(description = "추천 목록 items[].cellId를 중복 없이 1~30개. "
			+ "cellIds=1,2,3처럼 쉼표로 이어 한 번에 보냅니다. cellIds=1&cellIds=2처럼 나눠 보내면 400입니다.",
			explode = Explode.FALSE, example = "[81591941, 81655936, 81607932]",
			array = @ArraySchema(schema = @Schema(type = "integer", format = "int64"),
				minItems = 1, maxItems = 30, uniqueItems = true))
		@RequestParam List<Long> cellIds,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		return ApiResult.ok(service.reasons(new V2CellReasonsQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			V2RequestParams.required(TimeSlot.class, "slot", slot),
			cellIds)));
	}
}
