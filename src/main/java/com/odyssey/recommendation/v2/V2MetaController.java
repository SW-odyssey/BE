package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 초기 선택지")
public class V2MetaController {

	private final V2MetaService service;

	public V2MetaController(V2MetaService service) {
		this.service = service;
	}

	/** 분석 배치와 요일·시간대·대분류·가게 분류 선택지를 준다. 받는 쿼리 파라미터는 없다. */
	@GetMapping("/meta")
	public ApiResult<V2MetaResponse> meta(
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, Set.of());
		return ApiResult.ok(service.meta());
	}
}
