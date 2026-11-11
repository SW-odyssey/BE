package com.odyssey.recommendation;

import java.util.List;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 추천에서 받은 칸 ID로, 지도를 표시할 때 필요한 경계만 조회한다. */
@RestController
@RequestMapping("/api/v1/recommendations/cell")
@RequiredArgsConstructor
@Tag(name = "추천")
public class CellRecommendationController {

	private final CellRecommendationService cellRecommendationService;

	@GetMapping
	@Operation(summary = "추천 장소의 셀 경계 조회", description = """
		추천 결과 items의 cellId를 모아, 지도를 표시할 때만 호출합니다.
		cellIds=1,2처럼 쉼표로 구분하여 최대 30개를 보냅니다.
		중복 ID는 한 번만 반환하며 결과는 셀 ID 오름차순입니다.
		존재하지 않는 ID는 제외하고, 모두 없으면 HTTP 200과 cells=[]를 반환합니다.
		장소 추천을 다시 실행하지 않으며 붐빔 등급·점수는 포함하지 않습니다.
		""")
	@ApiResponse(responseCode = "200", description = "요청한 셀의 경계 (빈 목록 포함)",
		useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: ID 누락·개수 초과·잘못된 ID",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = """
				{"success":false,"data":null,"error":{"code":"INVALID_INPUT",
				"message":"cellIds 는 양의 정수 ID를 1개 이상 30개 이하로 보내야 합니다"}}
				""")))
	@ApiResponse(responseCode = "503", description = "DATA_UNAVAILABLE: DB 조회 실패",
		content = @Content(schema = @Schema(implementation = ApiResult.class)))
	@ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: 서버 내부 오류",
		content = @Content(schema = @Schema(implementation = ApiResult.class)))
	public ApiResult<CellRecommendationResponse> cells(
		@Parameter(description = "추천 결과 items[].cellId 목록. 중복을 포함한 입력 개수는 최대 30개",
			array = @ArraySchema(minItems = 1, maxItems = 30,
				schema = @Schema(type = "integer", format = "int64", minimum = "1")))
		@RequestParam List<Long> cellIds) {
		if (cellIds.isEmpty() || cellIds.size() > RecommendationSelector.MAX_RESULTS
			|| cellIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				"cellIds 는 양의 정수 ID를 1개 이상 30개 이하로 보내야 합니다");
		}
		return ApiResult.ok(cellRecommendationService.findCells(cellIds));
	}
}
