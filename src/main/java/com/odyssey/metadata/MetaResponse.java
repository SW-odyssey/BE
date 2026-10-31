package com.odyssey.metadata;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/meta 의 data. 필드 순서는 GitHub 이슈 #29 의 응답과 같다.
 *
 * @param timeZone           API 의 기준 시간대
 * @param supportedLanguages 지원하는 언어 (ko, ja, zh, en). 화면 표시명(label)은 아직 ko 만
 * @param visitDate          방문 날짜의 기본값(서울 기준 오늘). 고를 수 있는 범위는 오늘부터 1년 뒤까지이고, 벗어나면 추천 API 가 400 을 낸다
 * @param areas              지역
 * @param categories         카테고리
 * @param foodTypes          음식점 종류. category=food 일 때만 화면에 보인다
 * @param since              노포만 볼지의 기본값(false). 켜면 추천 API 에 since=true 를 보낸다. category=food 일 때만 화면에 보인다
 * @param slotsTime          시간대
 * @param densities          붐빔 정도
 * @param maxResults         추천 한 번에 내려 주는 최대 장소 수
 */
public record MetaResponse(
	String timeZone,
	List<String> supportedLanguages,
	LocalDate visitDate,
	List<Option> areas,
	List<Option> categories,
	List<Option> foodTypes,
	boolean since,
	List<Option> slotsTime,
	List<Option> densities,
	int maxResults) {
}
