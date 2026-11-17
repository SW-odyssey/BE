package com.odyssey.recommendation.v2;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.odyssey.global.response.ApiResult;
import com.odyssey.cellinsight.repository.CellInsightRepository.Place;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 장소 기본 정보")
public class V2PlaceController {
	private final V2PlaceService service;
	public V2PlaceController(V2PlaceService service) { this.service = service; }

	@GetMapping("/places/{id}")
	public ApiResult<Place> place(@PathVariable long id, @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestValidation.check(params, Set.of());
		return ApiResult.ok(service.place(id));
	}

}
