package com.odyssey.recommendation.v2;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 추천 목록")
public class V2RecommendationController {
	private final V2RecommendationService service;
	public V2RecommendationController(V2RecommendationService service) { this.service = service; }

	@GetMapping("/recommendations")
	public ApiResult<V2RecommendationResponse> recommend(@RequestParam String datasetId,
		@RequestParam String weekday, @RequestParam String slot,
		@RequestParam double lat, @RequestParam double lng,
		@RequestParam(defaultValue = "food") String category, @RequestParam(defaultValue = "all") String kind,
		@RequestParam(defaultValue = "") String q,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestValidation.check(params, Set.of("datasetId", "weekday", "slot", "lat", "lng", "category", "kind", "q"));
		return ApiResult.ok(service.recommend(new V2Query(datasetId, weekday, slot, lat, lng,
			category, kind, q)));
	}

}
