package com.odyssey.recommendation.v2;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 주변 방문 패턴")
public class V2VisitPatternController {
	private final V2VisitPatternService service;
	public V2VisitPatternController(V2VisitPatternService service) { this.service = service; }

	@GetMapping("/cells/{cellId}/visit-pattern")
	public ApiResult<V2VisitPatternResponse> profile(@PathVariable long cellId, @RequestParam String datasetId,
		@RequestParam String weekday, @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestValidation.check(params, Set.of("datasetId", "weekday"));
		return ApiResult.ok(service.profile(datasetId, weekday, cellId));
	}

}
