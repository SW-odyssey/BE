package com.odyssey.metadata;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 화면의 선택지 하나.
 *
 * @param id        요청에 쓰는 값. 서버 enum 이름을 소문자로 쓴다 (GitHub 이슈 #28)
 * @param label     화면에 보여 줄 이름
 * @param isDefault 처음 열었을 때 선택돼 있는지
 */
public record Option(
	@Schema(description = "추천 요청에 넣을 값", example = "all") String id,
	@Schema(description = "화면에 표시할 이름", example = "전체") String label,
	@Schema(description = "처음 표시할 때 기본으로 선택하는 항목인지 여부", example = "true")
	boolean isDefault) {
}
