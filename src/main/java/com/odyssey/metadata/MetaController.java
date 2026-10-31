package com.odyssey.metadata;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.global.api.VisitDateRange;
import com.odyssey.global.config.ClockConfig;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

/**
 * 앱을 열거나 필터 · 지역 · 방문 시간 시트를 열 때 FE 가 선택지와 기본값을 받아 가는 API (GitHub 이슈 #29).
 *
 * <p>DB 를 조회하지 않고 enum 과 고정값으로 만든다.
 * 지역이나 시간대가 바뀌어도 FE 수정 없이 반영되도록, 화면 표시명은 서버가 정한다.
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

	/** 추천 한 번에 내려 주는 최대 장소 수 */
	private static final int MAX_RESULTS = 200;

	private final Clock clock;

	public MetaController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping
	public ApiResult<MetaResponse> meta() {
		// 서버가 UTC 로 돌아도 서울 기준 오늘이 나오도록 주입받은 시계를 쓴다
		LocalDate today = LocalDate.now(clock);

		MetaResponse response = new MetaResponse(
			ClockConfig.SEOUL.getId(),
			List.of("ko", "ja", "zh", "en"),
			VisitDateRange.min(today),
			areas(),
			categories(),
			foodTypes(),
			false,
			slotsTime(),
			densities(),
			MAX_RESULTS);

		return ApiResult.ok(response);
	}

	private static List<Option> areas() {
		return List.of(
			new Option("all", "전체", true),
			option(Area.JONGNO, "종로", false),
			option(Area.IKSEON, "익선동", false),
			option(Area.INSADONG, "인사동", false),
			option(Area.EULJIRO, "을지로", false),
			option(Area.MYEONGDONG, "명동", false));
	}

	private static List<Option> categories() {
		return List.of(
			new Option("all", "전체", true),
			option(Category.FOOD, "음식점", false),
			option(Category.CAFE, "카페", false));
	}

	// FoodType.OTHER · BAR 는 화면에서 고를 수 없어서 내려 주지 않는다
	private static List<Option> foodTypes() {
		return List.of(
			new Option("any", "상관없음", true),
			option(FoodType.KOREAN, "한식", false));
	}

	private static List<Option> slotsTime() {
		return List.of(
			option(TimeSlot.LUNCH, "점심 11~13시", false),
			option(TimeSlot.AFTERNOON, "오후 14~17시", true),
			option(TimeSlot.DINNER, "저녁 18~21시", false),
			option(TimeSlot.LATE_NIGHT, "심야 22~01시", false));
	}

	// DensityLevel.MODERATE 는 화면에서 고를 수 없어서 내려 주지 않는다
	private static List<Option> densities() {
		return List.of(
			new Option("any", "상관없음", true),
			option(DensityLevel.LESS_BUSY, "덜 붐빔", false),
			option(DensityLevel.LIVELY, "활기참", false));
	}

	// id 를 enum 이름에서 만들어 오타를 막는다
	private static Option option(Enum<?> value, String label, boolean isDefault) {
		return new Option(ApiEnums.toValue(value), label, isDefault);
	}
}
