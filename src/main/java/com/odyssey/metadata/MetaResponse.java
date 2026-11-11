package com.odyssey.metadata;

import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/v1/meta 의 data. 필드 순서는 GitHub 이슈 #29 의 응답과 같다.
 *
 * @param timeZone           API 의 기준 시간대
 * @param supportedLanguages 지원하는 언어. 지금은 ko 만
 * @param visitDate          방문 날짜로 고를 수 있는 범위
 * @param areas              지역
 * @param categories         카테고리
 * @param foodTypes          음식점 종류. category=food 일 때만 화면에 보인다
 * @param since              노포 사용 여부(30년 이상 또는 공식 배지). category=food 일 때만 화면에 보인다
 * @param slots              시간대
 * @param densities          붐빔 정도
 * @param maxResults         추천 한 페이지에 내려 주는 최대 장소 수
 */
public record MetaResponse(
	@Schema(description = "날짜 계산 기준 시간대", example = "Asia/Seoul")
	String timeZone,
	@Schema(description = "지원하는 언어 코드", example = "[\"ko\"]")
	List<String> supportedLanguages,
	@Schema(description = "방문일로 선택할 수 있는 범위. 양끝 포함")
	VisitDate visitDate,
	@Schema(description = "지역 선택지. id를 areaId로 전달")
	List<Option> areas,
	@Schema(description = "매장 분류 선택지. id를 category로 전달")
	List<Option> categories,
	@Schema(description = "음식점 종류 선택지. category=food일 때만 사용")
	List<Option> foodTypes,
	@Schema(description = "노포 선택지. category=food일 때만 사용. id는 문자열 true 또는 false")
	List<Option> since,
	@Schema(description = "시간대 선택지. id를 slot으로 전달")
	List<Option> slots,
	@Schema(description = "붐빔 선택지. id를 density로 전달")
	List<Option> densities,
	@Schema(description = "추천 한 페이지에 반환하는 최대 장소 수", example = "30")
	int maxResults) {

	/**
	 * 방문 날짜 범위. 둘 다 포함한다.
	 *
	 * @param min 서울 기준 오늘
	 * @param max 오늘부터 6일 뒤
	 */
	public record VisitDate(
		@Schema(description = "선택 가능한 첫날. 서울 기준 오늘", format = "date") LocalDate min,
		@Schema(description = "선택 가능한 마지막 날. 서울 기준 오늘부터 6일 뒤", format = "date")
		LocalDate max) {
	}
}
