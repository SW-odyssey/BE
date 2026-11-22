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
import com.odyssey.store.entity.StoreKind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "v2 · 코스 추천")
public class V2CourseController {

	private static final Set<String> KEYS =
		Set.of("datasetId", "weekday", "startSlot", "lat", "lng", "excludeIds", "seed", "afternoon");
	private final V2CourseService service;

	public V2CourseController(V2CourseService service) {
		this.service = service;
	}

	@GetMapping("/api/v2/course-recommendations")
	@Operation(summary = "현재 위치에서 심야까지 이어지는 추천 코스", description = """
		점심 한식(12:30), 오후 카페 또는 구경(15:00, afternoon), 저녁 한식(19:00), 심야 주점(22:30) 중 시작 시간대부터 계산합니다.
		요일과 시각은 서울 기준입니다. 각 구간은 직전 장소에서 직선 300m 이내입니다.
		방문 점수가 1위와 3점 이내인 후보 중 seed로 무작위 선택하고, 응답 시점에 Google 정규 영업시간으로 확인합니다.
		공휴일·임시휴무와 도보 경로는 보장하지 않습니다.
		같은 seed를 보내면 같은 순서로 후보를 확인합니다. 다른 코스를 보려면 seed를 바꾸거나 빼세요.
		장소를 바꾸려면 해당 구간의 출발 좌표와 startSlot, 제외할 매장 ID를 보내세요.
		""")
	public ApiResult<V2CourseResponse> recommend(@RequestParam String datasetId, @RequestParam String weekday,
		@RequestParam String startSlot, @RequestParam double lat, @RequestParam double lng,
		@Parameter(description = "이미 방문하거나 건너뛸 매장 ID. 쉼표로 구분") @RequestParam(required = false) List<Long> excludeIds,
		@Parameter(description = "후보를 섞는 값. 없으면 서버가 정해 응답에 담는다") @RequestParam(required = false) Long seed,
		@Parameter(description = "오후 자리. cafe(기본) 또는 shop(구경)") @RequestParam(required = false) String afternoon,
		@Parameter(hidden = true) @RequestParam MultiValueMap<String, String> params) {
		V2RequestParams.check(params, KEYS);
		if (excludeIds != null && excludeIds.stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "excludeIds에는 양의 매장 ID가 필요합니다.");
		}
		return ApiResult.ok(service.recommend(V2RequestParams.requireDatasetId(datasetId),
			V2RequestParams.required(Weekday.class, "weekday", weekday),
			V2RequestParams.required(TimeSlot.class, "startSlot", startSlot), new GeoPoint(lat, lng),
			excludeIds == null ? Set.of() : Set.copyOf(excludeIds),
			seed == null ? ThreadLocalRandom.current().nextLong(1, 1L << 53) : seed,
			afternoonKind(afternoon)));
	}

	private static StoreKind afternoonKind(String value) {
		if (value == null || value.isBlank() || value.equals("cafe")) {
			return StoreKind.CAFE;
		}
		if (value.equals("shop")) {
			return StoreKind.SHOP;
		}
		throw new BusinessException(CommonErrorCode.INVALID_INPUT, "afternoon 은 cafe 또는 shop 입니다.");
	}
}
