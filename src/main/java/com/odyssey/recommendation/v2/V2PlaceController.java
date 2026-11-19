package com.odyssey.recommendation.v2;

import java.util.Set;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "v2 · 장소 기본 정보")
public class V2PlaceController {

	private final V2PlaceService service;

	public V2PlaceController(V2PlaceService service) {
		this.service = service;
	}

	/** 활성 매장의 기본 정보를 준다. 받는 쿼리 파라미터는 없고, id 는 양의 정수여야 한다. */
	@GetMapping("/places/{id}")
	public ApiResult<V2PlaceResponse> place(@PathVariable long id,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, Set.of());
		if (id <= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "id는 양의 정수입니다.");
		}
		return ApiResult.ok(service.place(id));
	}
}
