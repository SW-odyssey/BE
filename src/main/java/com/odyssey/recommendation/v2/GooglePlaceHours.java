package com.odyssey.recommendation.v2;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.odyssey.cellcondition.entity.Weekday;

/**
 * Google Place Details(New)로 영업 상태와 정규 영업시간을 확인한다.
 *
 * <p>정책상 Google 에서 받은 값은 저장하지 않고 이 요청의 판정에만 쓴다. 영업시간 필드 때문에 호출마다
 * Place Details Enterprise 요금이 붙는다. 공휴일·임시휴무는 정규 영업시간에 없어서 판정하지 못한다.
 */
@Component
class GooglePlaceHours implements CourseVisitChecker {

	private static final Logger log = LoggerFactory.getLogger(GooglePlaceHours.class);
	private static final String FIELDS = "businessStatus,regularOpeningHours.periods";
	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	private final RestClient client;
	private final String apiKey;

	GooglePlaceHours(@Value("${google.places.api-key:}") String apiKey,
		@Value("${google.places.base-url:https://places.googleapis.com}") String baseUrl) {
		var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		var factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	@Override
	public Result check(String googlePlaceId, Weekday weekday, LocalTime visitTime, int stayMinutes) {
		if (apiKey.isEmpty()) {
			log.warn("google.places.api-key 가 없어 코스 영업 확인을 할 수 없습니다.");
			return Result.UNAVAILABLE;
		}
		Place place;
		try {
			place = client.get().uri("/v1/places/{id}", googlePlaceId)
				.header("X-Goog-Api-Key", apiKey).header("X-Goog-FieldMask", FIELDS)
				.retrieve().body(Place.class);
		} catch (HttpClientErrorException.NotFound e) {
			return Result.NOT_OPEN;
		} catch (HttpClientErrorException e) {
			// 400 은 place_id 형식이 잘못된 매장 문제, 그 밖의 4xx(키·한도)는 서버 문제다
			if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
				return Result.NOT_OPEN;
			}
			log.warn("Google Place Details 조회 실패: {}", e.getStatusCode());
			return Result.UNAVAILABLE;
		} catch (RestClientException e) {
			log.warn("Google Place Details 조회 실패: {}", e.getClass().getSimpleName());
			return Result.UNAVAILABLE;
		}
		if (place == null || !"OPERATIONAL".equals(place.businessStatus())) {
			return Result.NOT_OPEN;
		}
		if (place.regularOpeningHours() == null) {
			return Result.HOURS_UNKNOWN;
		}
		return isOpen(place.regularOpeningHours().periods(), weekday, visitTime, stayMinutes)
			? Result.OPEN : Result.NOT_OPEN;
	}

	/**
	 * 도착 시각이 한 영업 구간 안이고 머무는 시간이 끝날 때까지 그 구간이 이어지는지 본다. Google 의 day 는
	 * 0(일)~6(토)이고, 자정이나 토→일을 넘는 구간도 있다. close 가 없는 구간은 24시간 영업이다. 마감 정각
	 * 도착은 영업시간 밖이고, 마감 정각에 나오는 것은 허용한다. 맞붙은 두 구간(예: 15시 마감·15시 재개)을
	 * 이어 보지는 않는다.
	 */
	static boolean isOpen(List<Period> periods, Weekday weekday, LocalTime time, int stayMinutes) {
		if (periods == null) {
			return false;
		}
		int at = minuteOfWeek((weekday.ordinal() + 1) % 7, time.getHour(), time.getMinute());
		for (Period period : periods) {
			if (period == null || period.open() == null) {
				continue;
			}
			if (period.close() == null) {
				return true;
			}
			int open = minuteOfWeek(period.open());
			int close = minuteOfWeek(period.close());
			if (close <= open) {
				close += MINUTES_PER_WEEK;
			}
			for (int arrive : new int[] {at, at + MINUTES_PER_WEEK}) {
				if (open <= arrive && arrive < close && arrive + stayMinutes <= close) {
					return true;
				}
			}
		}
		return false;
	}

	private static int minuteOfWeek(Point point) {
		return minuteOfWeek(point.day(), point.hour(), point.minute());
	}

	private static int minuteOfWeek(int day, int hour, int minute) {
		return day * MINUTES_PER_DAY + hour * 60 + minute;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Place(String businessStatus, OpeningHours regularOpeningHours) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OpeningHours(List<Period> periods) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Period(Point open, Point close) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Point(int day, int hour, int minute) {
	}
}
