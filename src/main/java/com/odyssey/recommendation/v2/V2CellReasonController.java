package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 셀 추천 이유")
public class V2CellReasonController {
	private final V2CellReasonService service;
	public V2CellReasonController(V2CellReasonService service) { this.service = service; }

	@GetMapping("/cells/reasons")
	public ApiResult<V2ReasonsResponse> reasons(@RequestParam String datasetId, @RequestParam String weekday,
		@RequestParam String slot, @RequestParam List<Long> cellIds,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestValidation.check(params, Set.of("datasetId", "weekday", "slot", "cellIds"));
		return ApiResult.ok(service.reasons(datasetId, weekday, slot, cellIds));
	}

}
