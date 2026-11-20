package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@Tag(name = "v2 · 초기 선택지")
public class V2MetaController {

	private final V2MetaService service;

	public V2MetaController(V2MetaService service) {
		this.service = service;
	}

	/** 분석 배치와 요일·시간대·대분류·가게 분류 선택지를 준다. 받는 쿼리 파라미터는 없다. */
	@GetMapping("/meta")
	@Operation(summary = "초기 선택지", description = """
		다른 v2 API에 넣을 값을 줍니다. 쿼리 파라미터는 받지 않습니다.

		**datasets가 12개인 이유**: 같은 칸이라도 달마다 붐비는 정도가 달라서, 추천을 달별로 따로 계산합니다.
		2025년 1월~12월을 한 달씩 분석해 배치가 12개 있습니다. referenceMonth가 그 배치의 달이고, 최신 달이 먼저 옵니다.
		고른 배치의 id를 다른 API의 datasetId로 그대로 보냅니다.

		**id 뒤에 알 수 없는 문자열이 붙는 이유**: 한번 공개한 배치는 고치지 않습니다.
		잘못된 데이터를 바로잡을 때는 같은 달이라도 새 id로 다시 올립니다.
		그래서 "2025-12"만 보고 id를 만들 수 없고, 이 응답에서 받아야 합니다. 코드에 id를 적어 두지 마세요.

		population은 모든 배치가 같은 값(DOMESTIC_20_39)이라 신경 쓰지 않아도 됩니다.
		FE 목 응답(mock/v2/mappings)과 모양을 맞추려고 남겨 둔 필드입니다.
		weekdays·slots·categories·kinds는 화면에 보여 줄 순서 그대로이며, all은 "조건 없음"입니다.
		공개된 배치가 하나도 없으면 오류가 아니라 HTTP 200과 datasets=[]입니다.
		""")
	@ApiResponse(responseCode = "200", description = "선택지 조회 성공 (배치가 없어도 200)",
		useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 쿼리 파라미터를 보낸 경우",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.UNKNOWN_KEY_EXAMPLE)))
	@ApiResponse(responseCode = "503", description = V2ApiDocs.DATA_UNAVAILABLE,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATA_UNAVAILABLE_EXAMPLE)))
	@ApiResponse(responseCode = "500", description = V2ApiDocs.INTERNAL_ERROR,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INTERNAL_ERROR_EXAMPLE)))
	public ApiResult<V2MetaResponse> meta(
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, Set.of());
		return ApiResult.ok(service.meta());
	}
}
