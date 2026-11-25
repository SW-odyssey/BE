package com.odyssey.recommendation.v2;

import java.text.Normalizer;
import java.util.List;
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

	private static final int DEFAULT_SEARCH_LIMIT = 6;
	private static final int MAX_SEARCH_LIMIT = 30;
	private static final int MAX_SEARCH_LENGTH = 80;

	private final V2PlaceService service;

	public V2PlaceController(V2PlaceService service) {
		this.service = service;
	}

	@GetMapping("/place-search")
	@Operation(summary = "다국어 매장 상호 검색 및 자동완성", description = """
		활성 매장의 한국어 원본 상호와 현재 상호에 연결된 영어·중국어 간체·일본어 표기를 함께 찾습니다.
		추천 위치, 방문 순위, 시간대와 무관합니다. 검색어는 대소문자·공백·전각 차이를 무시합니다.
		일치 순서는 정확히 일치, 시작 부분 일치, 나머지 부분 일치입니다. 같은 순위에서는 요청한 언어를
		우선하고, 이후 매장 ID로 정렬합니다. 후보가 없으면 items가 빈 배열로 반환됩니다.
		""")
	@ApiResponse(responseCode = "200", description = "상호 검색 성공", useReturnTypeSchema = true)
	@ApiResponse(responseCode = "400", description = "INVALID_INPUT: 검색어·표시 언어·조회 한도 오류")
	@ApiResponse(responseCode = "503", description = "DATA_UNAVAILABLE: DB 조회 실패")
	public ApiResult<SearchResponse> search(
		@Parameter(description = "검색할 상호 또는 상호 일부. 1~80자.", example = "을지갈매기")
		@RequestParam String q,
		@Parameter(description = V2RequestParams.LANGUAGE_DOC, example = "en")
		@RequestParam(required = false) String lang,
		@Parameter(description = "최대 반환 건수. 기본 6, 허용 1~30.", example = "6")
		@RequestParam(required = false) Integer limit,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, Set.of("q", "lang", "limit"));
		Language language = V2RequestParams.displayLanguage(lang);
		String query = Normalizer.normalize(q, Normalizer.Form.NFKC).strip();
		if (query.isEmpty() || query.length() > MAX_SEARCH_LENGTH || query.indexOf('\0') >= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "q는 유효한 상호 검색어 1~80자로 입력해 주세요.");
		}
		int maxResults = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
		if ((limit == null && params.containsKey("limit")) || maxResults < 1 || maxResults > MAX_SEARCH_LIMIT) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "limit은 1~30이어야 합니다.");
		}
		return ApiResult.ok(new SearchResponse(service.search(query, language, maxResults)));
	}

	public record SearchResponse(List<V2PlaceResponse> items) {}

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
