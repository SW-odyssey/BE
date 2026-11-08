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

	/** 방문 날짜는 오늘부터 6일 뒤까지 고를 수 있다 */
	private static final int VISIT_DAYS_AHEAD = 6;

	/** 추천 한 번에 내려 주는 최대 장소 수 */
	private static final int MAX_RESULTS = 30;

	private final Clock clock;

	/** 서버 시간대와 관계없이 서울 기준 날짜를 계산하도록 공통 Clock을 주입받는다. */
	public MetaController(Clock clock) {
		this.clock = clock;
	}

	/** 화면의 선택지·기본값·방문 날짜 범위와 추천 최대 개수를 반환한다. DB는 조회하지 않는다. */
	@GetMapping
	public ApiResult<MetaResponse> meta() {
		// 서버가 UTC 로 돌아도 서울 기준 오늘이 나오도록 주입받은 시계를 쓴다
		LocalDate today = LocalDate.now(clock);

		MetaResponse response = new MetaResponse(
			ClockConfig.SEOUL.getId(),
			List.of("ko"),
			new MetaResponse.VisitDate(today, today.plusDays(VISIT_DAYS_AHEAD)),
			areas(),
			categories(),
			foodTypes(),
			since(),
			slots(),
			densities(),
			MAX_RESULTS);

		return ApiResult.ok(response);
	}

	/** 지역 선택지. 기본은 특정 지역으로 제한하지 않는 전체다. */
	private static List<Option> areas() {
		return List.of(
			new Option("all", "전체", true),
			option(Area.JONGNO, "종로", false),
			option(Area.IKSEON, "익선동", false),
			option(Area.INSADONG, "인사동", false),
			option(Area.EULJIRO, "을지로", false),
			option(Area.MYEONGDONG, "명동", false));
	}

	/** 매장 분류 선택지. DB의 StoreKind와 별개로 기존 화면의 음식점/카페 구분을 유지한다. */
	private static List<Option> categories() {
		return List.of(
			new Option("all", "전체", true),
			option(Category.FOOD, "음식점", false),
			option(Category.CAFE, "카페", false));
	}

	// FoodType.OTHER 는 화면에서 고를 수 없어서 내려 주지 않는다
	/** 음식점을 고른 경우 사용할 종류 선택지를 반환한다. */
	private static List<Option> foodTypes() {
		return List.of(
			new Option("any", "상관없음", true),
			option(FoodType.KOREAN, "한식", false),
			option(FoodType.BAR, "술집", false));
	}

	// 30년 이상 또는 공식 노포 배지가 있는 가게를 고른다
	/** 노포 필터의 켜기/끄기 선택지. id는 추천 요청의 since 값과 같다. */
	private static List<Option> since() {
		return List.of(
			new Option("false", "상관없음", true),
			new Option("true", "노포", false));
	}

	/** 방문 시간대 선택지. 기본은 오후이며, 화면 라벨에 각 시간 범위를 함께 제공한다. */
	private static List<Option> slots() {
		return List.of(
			option(TimeSlot.LUNCH, "점심 11~13시", false),
			option(TimeSlot.AFTERNOON, "오후 14~17시", true),
			option(TimeSlot.DINNER, "저녁 18~21시", false),
			option(TimeSlot.LATE_NIGHT, "심야 22~01시", false));
	}

	// DensityLevel.MODERATE 는 화면에서 고를 수 없어서 내려 주지 않는다
	/** 붐빔 필터 선택지. lively는 조회할 때 peakHour 조건으로 해석한다. */
	private static List<Option> densities() {
		return List.of(
			new Option("any", "상관없음", true),
			option(DensityLevel.LESS_BUSY, "덜 붐빔", false),
			option(DensityLevel.LIVELY, "활기참", false));
	}

	// id 를 enum 이름에서 만들어 오타를 막는다
	/** enum 이름을 공통 API 소문자 규칙으로 바꾸고 화면 라벨과 기본 선택 여부를 붙인다. */
	private static Option option(Enum<?> value, String label, boolean isDefault) {
		return new Option(ApiEnums.toValue(value), label, isDefault);
	}
}
