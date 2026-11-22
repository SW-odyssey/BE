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
class GooglePlaceHours implements CourseHoursLookup {

	private static final Logger log = LoggerFactory.getLogger(GooglePlaceHours.class);
	private static final String FIELDS = "businessStatus,regularOpeningHours.periods";

	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	private final RestClient client;
	private final String apiKey;

	GooglePlaceHours(@Value("${google.places.api-key:}") String apiKey,
		@Value("${google.places.base-url:https://places.googleapis.com}") String baseUrl) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	@Override
	public Hours load(String googlePlaceId) {
		if (apiKey.isEmpty()) {
			log.warn("google.places.api-key 가 없어 코스 영업 확인을 할 수 없습니다.");
			return Hours.fixed(Result.UNAVAILABLE);
		}
		try {
			Place place = fetchPlaceHours(googlePlaceId);
			return (weekday, visitTime, stayMinutes) -> checkStayDuringOpeningHours(place, weekday, visitTime, stayMinutes);
		} catch (HttpClientErrorException.NotFound e) {
			return Hours.fixed(Result.NOT_OPEN);
		} catch (HttpClientErrorException e) {
			return Hours.fixed(resultForClientError(e));
		} catch (RestClientException e) {
			log.warn("Google Place Details 조회 실패: {}", e.getClass().getSimpleName());
			return Hours.fixed(Result.UNAVAILABLE);
		}
	}

	private Place fetchPlaceHours(String googlePlaceId) {
		return client.get().uri("/v1/places/{id}", googlePlaceId)
			.header("X-Goog-Api-Key", apiKey)
			.header("X-Goog-FieldMask", FIELDS)
			.retrieve().body(Place.class);
	}

	private static Result resultForClientError(HttpClientErrorException e) {
		// Google은 매장 ID 형식 오류와 만료된 API 키에 모두 400을 반환한다.
		if (e.getStatusCode() == HttpStatus.BAD_REQUEST && !isInvalidKey(e)) {
			return Result.NOT_OPEN;
		}
		log.warn("Google Place Details 조회 실패: {}", e.getStatusCode());
		return Result.UNAVAILABLE;
	}

	private static Result checkStayDuringOpeningHours(Place place, Weekday weekday, LocalTime visitTime, int stayMinutes) {
		if (place == null || !"OPERATIONAL".equals(place.businessStatus())) {
			return Result.NOT_OPEN;
		}
		if (place.regularOpeningHours() == null) {
			return Result.HOURS_UNKNOWN;
		}
		return coversStay(place.regularOpeningHours().periods(), weekday, visitTime, stayMinutes)
			? Result.OPEN : Result.NOT_OPEN;
	}

	private static boolean isInvalidKey(HttpClientErrorException e) {
		ErrorBody body;
		try {
			body = e.getResponseBodyAs(ErrorBody.class);
		} catch (RuntimeException unreadable) {
			return false;
		}
		if (body == null || body.error() == null || body.error().details() == null) {
			return false;
		}
		return body.error().details().stream()
			.anyMatch(detail -> detail != null && "API_KEY_INVALID".equals(detail.reason()));
	}

	/**
	 * 도착 시각이 한 영업 구간 안이고 머무는 시간이 끝날 때까지 그 구간이 이어지는지 본다. 마감 정각
	 * 도착은 영업시간 밖이고, 마감 정각에 나오는 것은 허용한다. 맞붙은 두 구간(예: 15시 마감·15시 재개)을
	 * 이어 보지는 않는다.
	 *
	 * @param periods 영업 구간. null 이면 영업하지 않는 것으로 본다
	 */
	static boolean coversStay(List<Period> periods, Weekday weekday, LocalTime arrival, int stayMinutes) {
		if (periods == null) {
			return false;
		}
		// Google의 요일 번호는 일요일이 0이다.
		int googleDay = (weekday.ordinal() + 1) % 7;
		int arrivalMinute = minuteOfWeek(googleDay, arrival.getHour(), arrival.getMinute());
		return periods.stream().anyMatch(period -> period != null && period.coversStay(arrivalMinute, stayMinutes));
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
		boolean coversStay(int arrivalMinute, int stayMinutes) {
			if (open == null) {
				return false;
			}
			if (close == null) {
				return true;
			}
			int openingMinute = minuteOfWeek(open);
			int closingMinute = minuteOfWeek(close);
			if (closingMinute <= openingMinute) {
				closingMinute += MINUTES_PER_WEEK;
			}
			return stayFitsBetween(openingMinute, closingMinute, arrivalMinute, stayMinutes)
				|| stayFitsBetween(openingMinute, closingMinute, arrivalMinute + MINUTES_PER_WEEK, stayMinutes);
		}

		private static boolean stayFitsBetween(int open, int close, int arrival, int stayMinutes) {
			return open <= arrival && arrival < close && arrival + stayMinutes <= close;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Point(int day, int hour, int minute) {
	}

	/** Google 오류 본문. details 의 reason 은 google.rpc.ErrorInfo 의 값이다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorBody(ErrorStatus error) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorStatus(List<ErrorDetail> details) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorDetail(String reason) {
	}
}
