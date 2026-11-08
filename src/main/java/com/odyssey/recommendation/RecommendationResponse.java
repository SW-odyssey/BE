package com.odyssey.recommendation;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.*;
import com.odyssey.store.repository.StoreCardRow;

/** 최대 30곳. 지도 칸은 별도 API로 제공할 예정이므로 이 응답에는 포함하지 않는다. */
public record RecommendationResponse(Applied applied, int total, List<Item> items) {
	public record Applied(LocalDate visitDate, DayType dayType, TimeSlot slot) {
	}

	public record Point(double lat, double lng) {
	}

	public record Item(
		Long id, String name, Category category, FoodType foodType, Area areaId,
		Point point, Long cellId, Integer since, String googlePlaceId, List<StoreBadge> badges) {
		static Item from(StoreCardRow row, List<StoreBadge> badges) {
			boolean cafe = row.kind() == StoreKind.CAFE;
			return new Item(row.id(), row.name(), cafe ? Category.CAFE : Category.FOOD,
				cafe ? null : FoodType.valueOf(row.kind().name()), row.area(),
				new Point(row.lat(), row.lng()), row.cellId(),
				row.sinceDate() == null ? null : row.sinceDate().getYear(),
				row.googlePlaceId(), List.copyOf(badges));
		}
	}

	public static RecommendationResponse empty(LocalDate date, DayType day, TimeSlot slot) {
		return of(date, day, slot, List.of(), Map.of());
	}

	public static RecommendationResponse of(LocalDate date, DayType day, TimeSlot slot,
		List<StoreCardRow> stores, Map<Long, List<StoreBadge>> badges) {
		return new RecommendationResponse(new Applied(date, day, slot), stores.size(),
			stores.stream().map(row -> Item.from(row, badges.getOrDefault(row.id(), List.of())))
				.toList());
	}
}
