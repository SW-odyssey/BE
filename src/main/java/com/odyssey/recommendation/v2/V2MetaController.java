package com.odyssey.recommendation.v2;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 초기 선택지")
public class V2MetaController {
	private final V2MetaService service;
	public V2MetaController(V2MetaService service) { this.service = service; }

	@GetMapping("/meta")
	public ApiResult<V2MetaResponse> meta(@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestValidation.check(params, Set.of());
		return ApiResult.ok(service.meta());
	}

}
