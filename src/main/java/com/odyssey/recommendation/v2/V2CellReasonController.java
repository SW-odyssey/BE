package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 셀 추천 이유")
public class V2CellReasonController {

	private static final Set<String> KEYS = Set.of("datasetId", "weekday", "slot", "cellIds");

	private final V2CellReasonService service;

	public V2CellReasonController(V2CellReasonService service) {
		this.service = service;
	}

	/**
	 * 목록에 나온 칸들의 추천 이유를 한 번에 준다. cellIds 는 쉼표로 구분한 중복 없는 양의 정수
	 * 1~30개다. 필수 키 누락 문구를 지키려고 {@code @RequestParam} 을 유지한다.
	 */
	@GetMapping("/cells/reasons")
	public ApiResult<V2ReasonsResponse> reasons(
		@RequestParam String datasetId,
		@RequestParam String weekday,
		@RequestParam String slot,
		@RequestParam List<Long> cellIds,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		return ApiResult.ok(service.reasons(new V2CellReasonsQuery(
			datasetId,
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			V2RequestParams.required(TimeSlot.class, "slot", slot),
			cellIds)));
	}
}
