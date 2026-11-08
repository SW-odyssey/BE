package com.odyssey.recommendation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.*;
import com.odyssey.store.repository.StoreCardRow;

/**
 * 추천 응답 (#43). 목록과 개수 미리보기를 채운다.
 * 지도 칸은 별도 API로 제공할 예정이므로 이 응답에는 포함하지 않는다.
 *
 * <p>enum 필드(category, areaId, dayType, slot)는 Jackson 설정으로 소문자가 된다 ("insadong").
 *
 * @param applied 서버가 채우거나 판단한 값. FE 가 보낸 그대로인 값은 돌려주지 않는다
 * @param total   "장소 N곳 보기" (S03). items 길이와 같고 최대 30이다
 * @param items   추천 가게. 이 순서가 추천 순서다
 */
public record RecommendationResponse(Applied applied, int total, List<Item> items) {
	/**
	 * @param visitDate 생략하면 서버가 오늘로 채운다
	 * @param dayType   방문일로 서버가 판단한 월~목 / 금 / 토 / 일
	 * @param slot      생략하면 서버가 오후로 채운다
	 */
	public record Applied(LocalDate visitDate, DayType dayType, TimeSlot slot) {
	}

	/** 지도 표시와 거리 계산에 쓴다 */
	public record Point(double lat, double lng) {
	}

	/**
	 * 목록 카드 한 장. category · foodType은 DB의 단일 kind에서 만든다.
	 *
	 * @param since         개업 연도 (이력 기준). 예) 1968. 미확정이면 null
	 * @param googlePlaceId 사진을 구글에서 가져올 때 쓴다
	 * @param badges        공식 노포 지정. 없으면 빈 목록
	 */
	public record Item(
		Long id, String name, Category category, FoodType foodType, Area areaId,
		Point point, Long cellId, Integer since, String googlePlaceId, List<StoreBadge> badges) {
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
	public static RecommendationResponse empty(LocalDate date, DayType day, TimeSlot slot) {
		return of(date, day, slot, List.of(), Map.of());
	}

	/**
	 * @param stores 이미 추천 순서로 정렬된 가게
	 * @param badges 선택된 가게의 ID별 공식 노포 배지
	 */
	public static RecommendationResponse of(LocalDate date, DayType day, TimeSlot slot,
		List<StoreCardRow> stores, Map<Long, List<StoreBadge>> badges) {
		return new RecommendationResponse(new Applied(date, day, slot), stores.size(),
			stores.stream().map(row -> Item.from(row, badges.getOrDefault(row.id(), List.of())))
				.toList());
	}
}
