package com.odyssey.recommendation.v2;

import java.util.List;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.TimeSlot;

/** 첫 화면의 선택지. 프론트는 여기서 받은 값만 골라 다음 요청에 보낸다. */
public record V2MetaResponse(
	List<DatasetOption> datasets,
	List<String> weekdays,
	List<String> slots,
	List<String> categories,
	List<String> kinds,
	String defaultDatasetId,
	List<AreaOption> areas,
	List<SlotWindow> slotWindows,
	ServiceHours serviceHours) {

	public record DatasetOption(String id, String referenceMonth, String population) {
	}

	public record AreaOption(Area id, String name, double centerLat, double centerLng) {
	}

	public record SlotWindow(TimeSlot slot, String from, String toExclusive, boolean endsNextDay) {
	}

	public record ServiceHours(String from, String toExclusive, boolean endsNextDay) {
	}
}
