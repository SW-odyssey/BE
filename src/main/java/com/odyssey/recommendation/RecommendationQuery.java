package com.odyssey.recommendation;

import java.time.LocalDate;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;

/** 위치는 둘 다 있거나 둘 다 null이다. openedBefore는 노포 필터의 30년 기준일이다. */
public record RecommendationQuery(
	Area area, Category category, FoodType foodType, LocalDate openedBefore,
	LocalDate visitDate, TimeSlot timeSlot, DensityLevel densityLevel,
	String query, Double lat, Double lng) {
}
