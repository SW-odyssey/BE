package com.odyssey.metadata;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/v1/meta 의 data. 필드 순서는 GitHub 이슈 #29 의 응답과 같다.
 *
 * @param timeZone           API 의 기준 시간대
 * @param supportedLanguages 지원하는 언어 (ko, ja, zh, en). 화면 표시명(label)은 아직 ko 만
 * @param visitDate          방문 날짜의 기본값(서울 기준 오늘). 날짜 범위는 제한하지 않는다
 * @param areas              지역
 * @param categories         카테고리
 * @param foodTypes          음식점 종류. category=food 일 때만 화면에 보인다
 * @param since              노포 사용 여부의 기본값(false). category=food 일 때만 화면에 보인다
 * @param timeSlots          시간대
 * @param densities          붐빔 정도
 * @param maxResults         추천 한 페이지에 내려 주는 최대 장소 수
 */
public record MetaResponse(
	@Schema(description = "날짜 계산 기준 시간대", example = "Asia/Seoul")
	String timeZone,
	@Schema(description = "지원하는 언어 코드. ko, ja, zh, en", example = "[\"ko\", \"ja\", \"zh\", \"en\"]")
	List<String> supportedLanguages,
	@Schema(description = "방문일 기본값. 서울 기준 오늘. 날짜 범위는 제한하지 않으며 추천 API의 visitDate로 보낸다",
		type = "string", format = "date", example = "2026-10-28")
	LocalDate visitDate,
	@Schema(description = "지역 선택지. id를 areaId로 전달")
	List<Option> areas,
	@Schema(description = "매장 분류 선택지. id를 category로 전달")
	List<Option> categories,
	@Schema(description = "음식점 종류 선택지. category=food일 때만 사용. 상관없음(any)과 한식(korean)만 제공")
	List<Option> foodTypes,
	@Schema(description = "노포 사용 여부의 기본값. category=food일 때만 사용. 켜면 추천 API에 since=true로 전달",
		example = "false")
	boolean since,
	@Schema(description = "시간대 선택지. id를 slot으로 전달. lunch, afternoon(기본), dinner, late_night")
	List<Option> timeSlots,
	@Schema(description = "붐빔 선택지. id를 density로 전달")
	List<Option> densities,
	@Schema(description = "추천 한 페이지에 반환하는 최대 장소 수", example = "30")
	int maxResults) {
}
