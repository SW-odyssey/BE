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

import io.swagger.v3.oas.annotations.Parameter;
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
	public ApiResult<V2VisitPatternResponse> visitPattern(
		@PathVariable long cellId,
		@RequestParam String datasetId,
		@RequestParam String weekday,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		return ApiResult.ok(service.visitPattern(new V2VisitPatternQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			cellId)));
	}
}
