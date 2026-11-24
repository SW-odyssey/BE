package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.global.response.ApiResult;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.StoreKind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "v2 · 코스 추천")
public class V2CourseController {

	private static final Set<String> KEYS =
		Set.of("datasetId", "weekday", "startSlot", "lat", "lng", "excludeIds", "seed", "afternoon", "lateNight", "walk", "lang");
	private final V2CourseService service;

	public V2CourseController(V2CourseService service) {
		this.service = service;
	}

	@GetMapping("/api/v2/course-recommendations")
	@Operation(summary = "현재 위치에서 시작하는 최대 여섯 곳의 추천 코스", description = """
		startSlot에 해당하는 첫 자리부터 계산합니다. 같은 시간대의 자리는 position으로 구분합니다.

		| position | 시간대 | 도착 | 종류와 체류 시간(분) | 포함 조건 |
		|---|---|---|---|---|
		| 1 | lunch | 12:00 | 한식 50 | 필수 |
		| 2 | afternoon | 14:00 | 카페 80 또는 쇼핑 30 | 필수 |
		| 3 | afternoon | 15:40 | 놀거리 60 또는 쇼핑 30 | 선택 |
		| 4 | dinner | 18:00 | 한식 60 | 필수 |
		| 5 | dinner | 19:30 | 놀거리 60 | 선택 |
		| 6 | late_night | 22:00 | 주점 70 | lateNight=true |

		평일 3번 자리의 칸 방문 백분위 하한은 60, 다른 자리는 75입니다.
		선택 자리를 채우지 못하면 skipped에 slot, position, reason을 담고 마지막으로 고른 장소에서 계속합니다.
		lateNight 생략 또는 false이면 6번 자리를 계산하지 않습니다. 필수 자리 실패는 interruption에 담습니다.
		쇼핑·놀거리는 정규 영업시간 정보가 없어도 포함될 수 있으며 이때 hoursUnknown은 true입니다.
		요일과 시각은 서울 기준입니다. 첫 매장은 출발점 300m 안에서 고릅니다.
		기본은 매장 사이 150~500m를 걷고 앞서 방문한 곳의 100m 안으로 돌아가지 않습니다.
		walk=short는 매장 사이 300m 이내이며 최소 거리와 이전 방문지 반경 제한을 두지 않습니다.
		빔 탐색은 매 자리에서 정류장 수, 방문 점수 합, 총 직선거리 순으로 코스 후보를 건너뛴 자리 조합마다 최대 300개 남깁니다.
		마지막에는 정류장이 가장 많은 코스 중 최고 점수에서 정류장 수 × 3점 이내인 코스를 모읍니다.
		그중 최단 거리보다 100m 이내로 더 걷는 코스까지 seed로 고릅니다.
		방문 순서대로 영업을 확인하고 닫힌 매장은 제외한 뒤 처음부터 다시 고릅니다.
		요청당 Google 조회는 최대 40곳입니다. 추가 조회가 필요하면 CHECK_LIMIT, 후보 소진은 NO_VERIFIED_MATCH입니다.
		공휴일·임시휴무와 도보 경로는 보장하지 않습니다.
		같은 seed를 보내면 같은 순서로 후보를 확인합니다. 다른 코스를 보려면 seed를 바꾸거나 빼세요.
		한 시간대의 장소를 다시 고르려면 그 시간대 시작 좌표와 startSlot, 제외할 매장 ID를 보내세요.
		leaveTime은 도착 시각에 머무는 시간을 더한 값이고, walkMinutes는 직선거리(m)×1.3을 분당 67m로 나눠 올림한 값입니다(최소 1분).
		reasons는 고른 이유를 정해진 순서(LOCAL_TOP, OPEN_DURING_STAY, PEAK_NOW, AREA_TYPE, SIMILAR_CHOICES)로 담고, 문장은 FE가 만듭니다.
		LOCAL_TOP의 percent는 가게가 아니라 가게가 있는 50m 칸의 방문 상위 %입니다.
		""")
	public ApiResult<V2CourseResponse> recommend(
		@Parameter(description = V2ApiDocs.DATASET_ID, example = V2ApiDocs.DATASET_ID_EXAMPLE) @RequestParam String datasetId,
		@Parameter(description = V2ApiDocs.WEEKDAY, example = "fri") @RequestParam String weekday,
		@Parameter(description = "코스를 시작할 시간대", example = "dinner") @RequestParam String startSlot,
		@Parameter(description = "출발 위도", example = "37.5655342") @RequestParam double lat,
		@Parameter(description = "출발 경도", example = "126.9903373") @RequestParam double lng,
		@Parameter(description = "이미 방문하거나 건너뛸 매장 ID. 쉼표로 구분") @RequestParam(required = false) List<Long> excludeIds,
		@Parameter(description = "후보를 섞는 값. 없으면 서버가 정해 응답에 담는다") @RequestParam(required = false) Long seed,
		@Parameter(description = "2번 자리의 종류. cafe(기본) 또는 shop(구경)") @RequestParam(required = false) String afternoon,
		@Parameter(description = "심야 주점 포함 여부. 기본 false") @RequestParam(required = false) String lateNight,
		@Parameter(description = "매장 사이 걷기 조건", example = "default",
			schema = @Schema(defaultValue = "default", allowableValues = {"default", "short"}))
		@RequestParam(required = false) String walk,
		@Parameter(description = V2RequestParams.LANGUAGE_DOC, example = "en")
		@RequestParam(required = false) String lang,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		Language language = V2RequestParams.displayLanguage(lang);
		Set<Long> excluded = parseExcludedStoreIds(excludeIds);
		String dataset = V2RequestParams.requireDatasetId(datasetId);
		Weekday visitDay = V2RequestParams.required(Weekday.class, "weekday", weekday);
		TimeSlot firstSlot = V2RequestParams.required(TimeSlot.class, "startSlot", startSlot);
		GeoPoint origin = new GeoPoint(lat, lng);
		V2CourseResponse course = service.recommend(dataset, visitDay, firstSlot, origin, excluded,
			seedOrRandom(seed), parseAfternoonKind(afternoon), includeLateNight(lateNight), walkPreference(walk));
		return ApiResult.ok(language == Language.KO ? course : course.withLanguage(language));
	}

	private static WalkPreference walkPreference(String value) {
		if (value == null || value.equals("default")) {
			return WalkPreference.DEFAULT;
		}
		if (value.equals("short")) {
			return WalkPreference.SHORT;
		}
		throw new BusinessException(CommonErrorCode.INVALID_INPUT, "walk 는 default 또는 short 입니다.");
	}

	private static boolean includeLateNight(String value) {
		if (value == null || value.equals("false")) {
			return false;
		}
		if (value.equals("true")) {
			return true;
		}
		throw new BusinessException(CommonErrorCode.INVALID_INPUT, "lateNight 는 true 또는 false 입니다.");
	}

	private static Set<Long> parseExcludedStoreIds(List<Long> excludeIds) {
		if (excludeIds == null) {
			return Set.of();
		}
		if (excludeIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "excludeIds에는 양의 매장 ID가 필요합니다.");
		}
		return Set.copyOf(excludeIds);
	}

	private static long seedOrRandom(Long seed) {
		return seed == null ? ThreadLocalRandom.current().nextLong(1, 1L << 53) : seed;
	}

	private static StoreKind parseAfternoonKind(String value) {
		if (value == null || value.isBlank() || value.equals("cafe")) {
			return StoreKind.CAFE;
		}
		if (value.equals("shop")) {
			return StoreKind.SHOP;
		}
		throw new BusinessException(CommonErrorCode.INVALID_INPUT, "afternoon 은 cafe 또는 shop 입니다.");
	}
}
