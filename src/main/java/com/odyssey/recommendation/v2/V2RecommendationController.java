package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 추천 목록")
public class V2RecommendationController {

	private static final String ALL = "all";
	private static final Set<String> KEYS =
		Set.of("datasetId", "weekday", "slot", "lat", "lng", "category", "kind", "q");

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
	public ApiResult<V2RecommendationResponse> recommend(
		@RequestParam String datasetId,
		@RequestParam String weekday,
		@RequestParam String slot,
		@RequestParam double lat,
		@RequestParam double lng,
		@RequestParam(defaultValue = "food") String category,
		@RequestParam(defaultValue = ALL) String kind,
		@RequestParam(defaultValue = "") String q,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		V2RecommendationQuery query = new V2RecommendationQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			V2RequestParams.required(TimeSlot.class, "slot", slot),
			new GeoPoint(lat, lng),
			ApiEnums.parse(Category.class, "category", category, ALL).orElse(null),
			ApiEnums.parse(StoreKind.class, "kind", kind, ALL).orElse(null),
			q);
		return ApiResult.ok(service.recommend(query));
	}
}
