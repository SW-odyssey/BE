package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.StoreNames.Language;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 장소 기본 정보")
public class V2PlaceController {

	private final V2PlaceService service;

	public V2PlaceController(V2PlaceService service) {
		this.service = service;
	}

	/** 활성 매장의 기본 정보를 준다. id는 양의 정수여야 한다. */
	@GetMapping("/places/{id}")
	@Operation(summary = "장소 기본 정보", description = """
		폐업하지 않은 매장의 기본 정보(이름, 종류, 좌표, 칸 번호, 개업일, 구글 장소 ID)를 줍니다.
		주소·사진·영업시간은 이 API에 없으니 googlePlaceId로 구글에서 가져옵니다.

		**datasetId를 받지 않는 이유**: 매장 정보는 달마다 바뀌는 분석 결과와 상관없습니다.
		그리고 추천 목록에 없는 매장도 저장 목록이나 공유 링크로 바로 열 수 있어야 합니다.
		id는 데이터를 새로 올려도 바뀌지 않으므로 저장·공유 링크에 그대로 써도 됩니다.
		""")
	@ApiResponse(responseCode = "200", description = "장소 조회 성공", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 0 이하의 id, 잘못된 lang 또는 지원하지 않는 쿼리 파라미터",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = """
				{"success":false,"data":null,"error":{"code":"INVALID_INPUT",
				"message":"id는 양의 정수입니다."}}
				""")))
	@ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND: 없는 번호이거나 폐업한 매장. "
		+ "화면은 목록으로 돌아갑니다.",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = """
				{"success":false,"data":null,"error":{"code":"PLACE_NOT_FOUND",
				"message":"현재 제공하는 장소가 아닙니다."}}
				""")))
	@ApiResponse(responseCode = "503", description = "DATA_UNAVAILABLE: DB 조회 실패",
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.DATA_UNAVAILABLE_EXAMPLE)))
	@ApiResponse(responseCode = "500", description = V2ApiDocs.INTERNAL_ERROR,
		content = @Content(schema = @Schema(implementation = ApiResult.class),
			examples = @ExampleObject(value = V2ApiDocs.INTERNAL_ERROR_EXAMPLE)))
	public ApiResult<V2PlaceResponse> place(
		@Parameter(description = "매장 번호. 추천 목록의 items[].id입니다.", example = "1557")
		@PathVariable long id,
		@Parameter(description = V2RequestParams.LANGUAGE_DOC, example = "en")
		@RequestParam(required = false) String lang,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, Set.of("lang"));
		Language language = V2RequestParams.displayLanguage(lang);
		if (id <= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "id는 양의 정수입니다.");
		}
		V2PlaceResponse response = service.place(id);
		return ApiResult.ok(language == Language.KO ? response : response.withLanguage(language));
	}
}
