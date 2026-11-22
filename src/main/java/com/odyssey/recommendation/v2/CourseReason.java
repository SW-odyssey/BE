package com.odyssey.recommendation.v2;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * 코스 정류장 한 곳을 고른 이유 하나. 서버는 코드와 값만 보내고 문장은 FE 가 언어별로 만든다.
 *
 * <p>응답에서는 {@code {"code":"LOCAL_TOP","percent":3}}처럼 코드와 그 코드의 값이 한 객체에 담긴다.
 * 새 코드는 record 하나와 {@link CourseReasons} 의 규칙 한 줄만 더한다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "code")
public sealed interface CourseReason {

	/**
	 * 가게가 있는 50m 칸이 방문 상위 몇 % 인지. 가게 자체의 순위가 아니다.
	 *
	 * @param percent 1 이상. 상위 0.4% 도 1 로 보낸다
	 */
	@JsonTypeName("LOCAL_TOP")
	record LocalTop(int percent) implements CourseReason {
	}

	/** 도착부터 나오는 시각까지 Google 정규 영업시간 안이다. 영업시간을 모르는 곳은 붙지 않는다. */
	@JsonTypeName("OPEN_DURING_STAY")
	record OpenDuringStay(String from, String to) implements CourseReason {
	}

	/** 그 칸·요일에서 가장 붐비는 시간대에 방문한다. */
	@JsonTypeName("PEAK_NOW")
	record PeakNow() implements CourseReason {
	}

	/** @param type 동네 시간 유형. late_night, weekend_day, weekday_day, mixed 중 하나 */
	@JsonTypeName("AREA_TYPE")
	record AreaType(String type) implements CourseReason {
	}

	/** @param count 1위와 비슷한 점수(3점 이내)라 같이 섞은 후보 수. 고른 곳을 포함한다 */
	@JsonTypeName("SIMILAR_CHOICES")
	record SimilarChoices(int count) implements CourseReason {
	}
}
