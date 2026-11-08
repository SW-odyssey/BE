package com.odyssey.store.repository;

import java.time.LocalDate;
import com.odyssey.cell.entity.Area;
import com.odyssey.store.entity.StoreKind;

public record StoreCardRow(
	Long id, String name, StoreKind kind, Long cellId, Area area,
	double lat, double lng, LocalDate sinceDate, String googlePlaceId) {
}
