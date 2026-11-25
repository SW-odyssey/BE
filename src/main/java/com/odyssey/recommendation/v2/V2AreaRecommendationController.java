package com.odyssey.recommendation.v2;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cell.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class V2AreaRecommendationController {

	private static final Set<String> KEYS =
		Set.of("datasetId", "weekday", "slot", "areaId", "categories", "page", "size", "lang");

	private final V2AreaRecommendationService service;

	@GetMapping("/api/v2/area-recommendations")
	@Operation(summary = "지역별 추천 탐색", description = """
		선택한 지역이나 서비스 지역 전체에서 방문 백분위 75 이상인 활성 매장을 조회합니다.
		페이지는 0부터 시작합니다. 순서는 방문 백분위 내림차순, 매장 ID 오름차순입니다.
		전체 개수(total)는 페이지로 자르기 전 조건에 맞는 수입니다.
		""")
	public ApiResult<V2AreaRecommendationResponse> recommend(
		@RequestParam String datasetId,
		@RequestParam String weekday,
		@RequestParam String slot,
		@Parameter(description = "all, jongno, ikseon, insadong, euljiro, myeongdong",
			schema = @Schema(defaultValue = "all"))
		@RequestParam(required = false) String areaId,
		@Parameter(description = "쉼표로 구분한 종류. 기본 food,cafe. 명시적으로 shop, play도 선택 가능")
		@RequestParam(defaultValue = "food,cafe") String categories,
		@RequestParam(defaultValue = "0") int page,
		@Parameter(description = "페이지 크기 1~30")
		@RequestParam(defaultValue = "30") int size,
		@Parameter(description = V2RequestParams.LANGUAGE_DOC)
		@RequestParam(required = false) String lang,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		validateRequest(params, datasetId, page, size);
		AreaFilters filters = parseFilters(weekday, slot, areaId, categories, lang);

		return ApiResult.ok(service.recommend(datasetId, filters.weekday(), filters.slot(), filters.area(),
			filters.kinds(), page, size, filters.language()));
	}

	private static void validateRequest(MultiValueMap<String, String> params, String datasetId, int page, int size) {
		V2RequestParams.check(params, KEYS);
		V2RequestParams.requireDatasetId(datasetId);

		if (page < 0 || size < 1 || size > 30) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상, size는 1~30이어야 합니다.");
		}
	}

	private static AreaFilters parseFilters(String weekday, String slot, String areaId, String categories, String lang) {
		Weekday parsedWeekday = V2RequestParams.required(Weekday.class, "weekday", weekday);
		TimeSlot parsedSlot = V2RequestParams.required(TimeSlot.class, "slot", slot);
		Area area = parseArea(areaId);
		Language language = V2RequestParams.displayLanguage(lang);

		return new AreaFilters(parsedWeekday, parsedSlot, area, parseStoreKinds(categories), language);
	}

	private static Area parseArea(String areaId) {
		return areaId == null || "all".equals(areaId)
			? null : V2RequestParams.required(Area.class, "areaId", areaId);
	}

	private static Set<StoreKind> parseStoreKinds(String categories) {
		return storeKindsFor(parseCategories(categories));
	}

	private static Set<Category> parseCategories(String categories) {
		return Arrays.stream(categories.split(",", -1))
			.map(String::strip)
			.map(value -> V2RequestParams.required(Category.class, "categories", value))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
	}

	private static Set<StoreKind> storeKindsFor(Set<Category> categories) {
		return Arrays.stream(StoreKind.values())
			.filter(kind -> categories.contains(kind.category()))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(StoreKind.class)));
	}

	private record AreaFilters(Weekday weekday, TimeSlot slot, Area area, Set<StoreKind> kinds, Language language) {
	}
}
