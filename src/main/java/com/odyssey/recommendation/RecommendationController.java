package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 추천 API (GitHub 이슈 #30). GET /api/v1/recommendations
 *
 * <p>여기서는 요청 문자열을 검증하고 enum · 날짜로 바꾸는 일만 한다.
 * "insadong" → Area.INSADONG, "all" · "any" → null(조건 없음), "20" → 20년 전 날짜.
 * 추천 로직은 {@link RecommendationService} 가 맡는다.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	/** 방문일은 오늘부터 6일 뒤까지 고를 수 있다 (meta API 와 같은 값) */
	private static final int VISIT_DAYS_AHEAD = 6;
	/** 검색어 최대 길이 (#30) */
	private static final int MAX_QUERY_LENGTH = 80;
	/** 노포 기준으로 고를 수 있는 "N년 이상" (meta API 의 since 선택지와 같은 값) */
	private static final List<String> SINCE_YEARS = List.of("20", "30", "40");

	private final RecommendationService recommendationService;
	// 서버가 UTC 로 돌아도 서울 기준 "오늘"이 나오도록 주입받은 시계를 쓴다 (ClockConfig)
	private final Clock clock;

	/**
	 * 파라미터 이름은 FE 가 보내는 이름(#30)과 같아야 한다. 다르면 값이 조용히 무시된다.
	 *
	 * <pre>
	 * GET /api/v1/recommendations?areaId=insadong&category=food&foodType=korean
	 *     &since=20&visitDate=2026-10-25&slot=dinner&density=less_busy
	 * </pre>
	 */
	@GetMapping
	public ApiResult<RecommendationResponse> recommend(
		@RequestParam(required = false) String areaId,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) String foodType,
		@RequestParam(required = false) String since,
		@RequestParam(required = false) LocalDate visitDate,
		@RequestParam(required = false) String slot,
		@RequestParam(required = false) String density,
		@RequestParam(required = false) String q) {

		LocalDate today = LocalDate.now(clock);
		LocalDate date = toVisitDate(visitDate, today);
		validateQuery(q);

		// "all" 이나 생략 → null (전체). 모르는 값이면 ApiEnums 가 INVALID_INPUT 을 던진다
		Category findCategory = ApiEnums.parse(Category.class, "category", category, "all")
			.orElse(null);

		// foodType · since 는 음식점일 때만 의미가 있다 (#30).
		// 카페나 전체를 골랐는데 foodType=korean 이 같이 오면 무시한다
		boolean food = findCategory == Category.FOOD;
		FoodType findFoodType = food
			? ApiEnums.parse(FoodType.class, "foodType", foodType, "any").orElse(null)
			: null;
		LocalDate openedBefore = food ? toOpenedBefore(since, today) : null;

		RecommendationQuery query = new RecommendationQuery(
			ApiEnums.parse(Area.class, "areaId", areaId, "all").orElse(null),
			findCategory,
			findFoodType,
			openedBefore,
			date,
			ApiEnums.parse(TimeSlot.class, "slot", slot, TimeSlot.AFTERNOON), // 생략하면 오후 (#30)
			ApiEnums.parse(DensityLevel.class, "density", density, "any").orElse(null),
			q);

		return ApiResult.ok(recommendationService.recommend(query));
	}

	/**
	 * 방문일을 정한다. 생략하면 오늘이다.
	 *
	 * @throws BusinessException 오늘 ~ 6일 뒤 범위 밖이면 INVALID_INPUT
	 */
	private static LocalDate toVisitDate(LocalDate visitDate, LocalDate today) {
		LocalDate date = visitDate == null ? today : visitDate;
		if (date.isBefore(today) || date.isAfter(today.plusDays(VISIT_DAYS_AHEAD))) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				"visitDate 는 오늘부터 " + VISIT_DAYS_AHEAD + "일 뒤까지입니다: " + date);
		}
		return date;
	}

	/** @throws BusinessException 검색어가 80자를 넘으면 INVALID_INPUT */
	private static void validateQuery(String q) {
		if (q != null && q.length() > MAX_QUERY_LENGTH) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				"q 는 " + MAX_QUERY_LENGTH + "자까지입니다");
		}
	}

	/**
	 * 노포 기준 "N년 이상" 을 날짜로 바꾼다. 예) since=20, 오늘 2026-10-25 → 2006-10-25 까지 개업한 곳
	 *
	 * <p>기준을 오늘로 할지 방문일로 할지는 아직 정하지 않았다 (최대 6일 차이)
	 *
	 * <p>숫자이기만 하면 받지 않고 선택지 값만 받는다. 큰 수는 날짜 계산이나 DB 에서 500 이 나고,
	 * 음수는 미래 날짜가 되어 조건이 의미를 잃는다.
	 *
	 * @return 생략했거나 "any" 면 null (상관없음)
	 * @throws BusinessException 20 · 30 · 40 이 아니면 INVALID_INPUT
	 */
	private static LocalDate toOpenedBefore(String since, LocalDate today) {
		if (since == null || since.isBlank() || since.equals("any")) {
			return null;
		}
		if (!SINCE_YEARS.contains(since)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				"since 값이 올바르지 않습니다: " + since + " (가능한 값: any, " + String.join(", ", SINCE_YEARS) + ")");
		}
		return today.minusYears(Integer.parseInt(since));
	}
}
