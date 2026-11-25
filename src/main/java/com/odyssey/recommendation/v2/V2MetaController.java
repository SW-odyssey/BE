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

		**defaultDatasetId**: 서버가 고른 기본 배치 id로, 가장 최근 기준월의 배치입니다. 처음 화면은 이 값으로 시작하세요.
		공개된 배치가 없으면 null입니다.

		**areas**: 서비스 지역 목록입니다. id는 장소 응답의 areaId와 같은 값이고
		jongno, ikseon, insadong, euljiro, myeongdong 중 하나입니다. centerLat·centerLng는 그 지역에 속한 50m 칸 중심 좌표의
		평균이라 지도를 옮길 때 기준으로 쓰는 값이고, 지역 경계는 아닙니다. 칸이 적재되지 않은 지역은 목록에 없습니다.

		**slotWindows·serviceHours**: 시간대 경계입니다. from은 포함, toExclusive는 포함하지 않는 시각이고
		endsNextDay가 true면 다음 날 그 시각에 끝납니다. 시간대는 빈틈 없이 이어져 점심 11:00부터 심야 다음 날 02:00까지가
		서비스 시간이며, 그 밖의 시각에는 추천 데이터가 없습니다. 자정 이후 시각을 어느 요일로 집계하는지는 분석팀 확인 후 반영합니다.

		**areaId**: 장소 상세·추천 목록·코스 장소 응답의 areaId는 장소가 속한 칸의 법정동으로 정해서 모든 장소에 있습니다.
		지역이 없는 장소는 없으므로 미분류 값은 두지 않습니다.
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
