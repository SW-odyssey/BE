package com.odyssey.recommendation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.*;
import com.odyssey.store.repository.StoreCardRow;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 추천 응답 (#43). 목록과 개수 미리보기를 채운다.
 * 지도 칸 경계는 필요할 때 /api/v1/recommendations/cell로 조회하므로 이 응답에는 포함하지 않는다.
 *
 * <p>enum 필드(category, areaId, dayType, slot)는 Jackson 설정으로 소문자가 된다 ("insadong").
 *
 * @param applied 서버가 채우거나 판단한 값. FE 가 보낸 그대로인 값은 돌려주지 않는다
 * @param total   이번 페이지의 장소 수. items 길이와 같고 최대 30이며 전체 검색 결과 수가 아니다
 * @param items   추천 가게. 이 순서가 추천 순서다
 * @param page    요청한 페이지 번호. 0부터 시작한다
 * @param hasNext 같은 조건으로 조회할 다음 페이지가 있는지 여부
 */
@Schema(description = "추천 한 페이지. 지도 칸 경계는 별도 API로 조회합니다.")
public record RecommendationResponse(
	@Schema(description = "서버가 확정한 방문일·요일 묶음·시간대") Applied applied,
	@Schema(description = "이번 페이지의 장소 수. items 길이와 같으며 전체 검색 결과 수가 아닙니다.",
		minimum = "0", maximum = "30")
	int total,
	@ArraySchema(arraySchema = @Schema(description = "추천 순서의 장소 목록. 결과가 없으면 빈 배열"),
		maxItems = 30)
	List<Item> items,
	@Schema(description = "요청한 페이지 번호. 0부터 시작", minimum = "0", example = "0") int page,
	@Schema(description = "다음 페이지가 있으면 true. 같은 조건으로 page를 1 늘려 요청합니다.")
	boolean hasNext) {
	/**
	 * @param visitDate 생략하면 서버가 오늘로 채운다
	 * @param dayType   방문일로 서버가 판단한 월~목 / 금 / 토 / 일
	 * @param slot      생략하면 서버가 오후로 채운다
	 */
	public record Applied(
		@Schema(description = "적용한 방문일. 요청에서 생략하면 서울 기준 오늘", format = "date")
		LocalDate visitDate,
		@Schema(description = "방문일의 요일 묶음. 공휴일도 해당 요일로 분류합니다.",
			implementation = String.class, allowableValues = {"mon_thu", "fri", "sat", "sun"})
		DayType dayType,
		@Schema(description = "적용한 방문 시간대", implementation = String.class,
			allowableValues = {"lunch", "afternoon", "dinner", "late_night"}) TimeSlot slot) {
	}

	/** 지도 표시와 거리 계산에 쓴다 */
	public record Point(
		@Schema(description = "매장 위도(WGS84, 도)", example = "37.5718") double lat,
		@Schema(description = "매장 경도(WGS84, 도)", example = "126.9827") double lng) {
	}

	/**
	 * 목록 카드 한 장. category · foodType은 DB의 단일 kind에서 만든다.
	 *
	 * @param since         개업 연도 (이력 기준). 예) 1968. 미확정이면 null
	 * @param googlePlaceId 사진을 구글에서 가져올 때 쓴다
	 * @param badges        공식 노포 지정. 없으면 빈 목록
	 */
	public record Item(
		@Schema(description = "매장 DB 식별자. 구글 place ID와 다릅니다.", example = "83") Long id,
		@Schema(description = "매장 이름", example = "예시식당") String name,
		@Schema(description = "매장 분류", implementation = String.class,
			allowableValues = {"food", "cafe"}) Category category,
		@Schema(description = "음식점 종류. 카페이면 null", nullable = true,
			implementation = String.class, allowableValues = {"korean", "bar", "other"}) FoodType foodType,
		@Schema(description = "매장이 속한 지역", implementation = String.class,
			allowableValues = {"jongno", "ikseon", "insadong", "euljiro", "myeongdong"}) Area areaId,
		@Schema(description = "매장 위치. 사용자 위치가 아닙니다.") Point point,
		@Schema(description = "매장이 속한 50m 칸 번호. 지도 경계는 "
			+ "/api/v1/recommendations/cell의 cellIds로 조회", example = "81383966") Long cellId,
		@Schema(description = "인허가 이력을 연결한 개업 연도. 미확정이면 null. "
			+ "요청의 since(노포 필터)와 달리 연도입니다.", example = "1974", nullable = true) Integer since,
		@Schema(description = "구글에서 사진·주소·리뷰·영업시간을 조회할 때 사용하는 ID") String googlePlaceId,
		@ArraySchema(arraySchema = @Schema(description = "공식 노포 배지. 없으면 빈 배열. "
			+ "century_store=백년가게, orae_gage=서울 오래가게, future_heritage=서울미래유산"),
			schema = @Schema(implementation = String.class,
				allowableValues = {"century_store", "orae_gage", "future_heritage"}))
		List<StoreBadge> badges) {
		/**
		 * DB의 단일 분류를 기존 API의 category · foodType으로 나누고 개업일은 연도로 바꾼다.
		 * 개업일이 미확정이면 since를 null로 유지한다.
		 */
		static Item from(StoreCardRow row, List<StoreBadge> badges) {
			boolean cafe = row.kind() == StoreKind.CAFE;
			return new Item(row.id(), row.name(), cafe ? Category.CAFE : Category.FOOD,
				cafe ? null : FoodType.valueOf(row.kind().name()), row.area(),
				new Point(row.lat(), row.lng()), row.cellId(),
				row.sinceDate() == null ? null : row.sinceDate().getYear(),
				row.googlePlaceId(), List.copyOf(badges));
		}
	}

	/** 조건에 맞는 칸이나 가게가 없을 때. 오류가 아니라 200 과 빈 목록 (#30) */
	public static RecommendationResponse empty(LocalDate date, DayType day, TimeSlot slot, int page) {
		return of(date, day, slot, RecommendationPage.from(List.of(), page), Map.of());
	}

	/**
	 * @param page 이미 추천 순서로 정렬하고 잘라 낸 한 페이지
	 * @param badges 선택된 가게의 ID별 공식 노포 배지
	 */
	static RecommendationResponse of(LocalDate date, DayType day, TimeSlot slot,
		RecommendationPage page, Map<Long, List<StoreBadge>> badges) {
		return new RecommendationResponse(new Applied(date, day, slot), page.stores().size(),
			page.stores().stream().map(row -> Item.from(row, badges.getOrDefault(row.id(), List.of())))
				.toList(), page.number(), page.hasNext());
	}
}
