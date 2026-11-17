package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Locale;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** datasetId로 분석 월과 정책을 고정한다. 반경은 도보거리가 아닌 직선거리다. */
public record V2Query(String datasetId, String weekday, String slot, double lat, double lng,
	String category, String kind, String q) {
	public static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
	public static final List<String> SLOTS = List.of("lunch", "afternoon", "dinner", "late_night");

	public V2Query {
		datasetId = dataset(datasetId);
		weekday = option(weekday, DAYS, "weekday");
		slot = option(slot, SLOTS, "slot");
		category = option(category, List.of("food", "cafe", "all"), "category");
		kind = option(kind, List.of("all", "korean", "bar", "other", "cafe"), "kind");
		if (!Double.isFinite(lat) || !Double.isFinite(lng)
			|| Math.abs(lat) > 90 || Math.abs(lng) > 180) {
			throw invalid("유효한 위도·경도가 필요합니다.");
		}
		q = q == null ? "" : q.strip();
		if (q.length() > 80) throw invalid("q는 80자까지입니다.");
		if ((category.equals("food") && kind.equals("cafe"))
			|| (category.equals("cafe") && !List.of("all", "cafe").contains(kind))) {
			throw invalid("category와 kind가 서로 맞지 않습니다.");
		}
		category = category.equals("all") ? null : category;
		kind = kind.equals("all") ? null : kind;
	}

	static String dataset(String value) {
		if (value == null || !value.matches("[a-zA-Z0-9_-]{1,80}")) {
			throw invalid("meta에서 제공한 datasetId가 필요합니다.");
		}
		return value;
	}

	static String option(String value, List<String> allowed, String field) {
		String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
		if (!allowed.contains(normalized)) throw invalid(field + " 허용값: " + String.join(", ", allowed));
		return normalized;
	}

	static BusinessException invalid(String message) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
	}
}
