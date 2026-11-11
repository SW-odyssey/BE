package com.odyssey.health;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.global.response.ApiResult;

@RestController
@Tag(name = "상태", description = "서버 응답 확인")
public class HealthController {

	@GetMapping("/health")
	@Operation(summary = "서버 응답 확인",
		description = "data에 {\"Status\":\"Ok\"}를 반환합니다. DB 연결 상태는 확인하지 않습니다.")
	public ApiResult<Map<String, String>> health() {
		return ApiResult.ok(Map.of("Status", "Ok"));
	}
}
