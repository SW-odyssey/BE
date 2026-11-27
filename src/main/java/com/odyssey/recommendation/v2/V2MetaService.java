package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cell.TimeSlot;
import com.odyssey.cell.repository.AreaCenter;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.recommendation.v2.V2MetaResponse.AreaOption;
import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;
import com.odyssey.recommendation.v2.V2MetaResponse.ServiceHours;
import com.odyssey.recommendation.v2.V2MetaResponse.SlotWindow;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

/** 첫 화면에서 고를 수 있는 선택지를 만든다. 선택지의 순서는 프론트 목(meta.json)과의 약속이다. */
@Service
@Transactional(readOnly = true)
public class V2MetaService {

	private static final String ALL = "all";
	private static final int COORDINATE_DECIMALS = 6;

	// 선언 순서(CAFE 가 첫째)와 달리 화면은 한식, 술집, 기타, 카페, 쇼핑, 놀거리 순으로 보여 준다
	private static final List<StoreKind> KIND_ORDER =
		List.of(StoreKind.KOREAN, StoreKind.BAR, StoreKind.OTHER, StoreKind.CAFE, StoreKind.SHOP, StoreKind.PLAY);

	private final V2DatasetResolver datasets;
	private final CellRepository cells;

	public V2MetaService(V2DatasetResolver datasets, CellRepository cells) {
		this.datasets = datasets;
		this.cells = cells;
	}

	/**
	 * @return 지원하는 공개 배치(기준월 내림차순)와 고정 선택지, 서비스 지역, 시간대 경계.
	 *         배치가 없어도 고정 선택지는 준다
	 */
	public V2MetaResponse meta() {
		List<DatasetOption> options = datasetOptions();
		return new V2MetaResponse(
			options,
			values(Arrays.asList(Weekday.values())),
			values(Arrays.asList(TimeSlot.values())),
			withAll(Arrays.asList(Category.values())),
			withAll(KIND_ORDER),
			newestDatasetId(options),
			areaOptionsWithCells(),
			slotWindows(),
			serviceHours());
	}

	private List<DatasetOption> datasetOptions() {
		return datasets.findSupported().stream()
			.map(dataset -> new DatasetOption(dataset.id(), dataset.referenceMonth(), dataset.population()))
			.toList();
	}

	private static String newestDatasetId(List<DatasetOption> optionsNewestFirst) {
		return optionsNewestFirst.isEmpty() ? null : optionsNewestFirst.get(0).id();
	}

	private List<AreaOption> areaOptionsWithCells() {
		return cells.findAreaCenters().stream()
			.sorted(Comparator.comparing(AreaCenter::area))
			.map(V2MetaService::toAreaOption)
			.toList();
	}

	private static AreaOption toAreaOption(AreaCenter center) {
		return new AreaOption(center.area(), center.area().koreanName(),
			roundCoordinate(center.lat()), roundCoordinate(center.lng()));
	}

	private static List<SlotWindow> slotWindows() {
		return Arrays.stream(TimeSlot.values())
			.map(slot -> new SlotWindow(
				slot, slot.startTime().toString(), slot.endTimeExclusive().toString(), slot.endsNextDay()))
			.toList();
	}

	private static ServiceHours serviceHours() {
		TimeSlot[] slots = TimeSlot.values();
		TimeSlot first = slots[0];
		TimeSlot last = slots[slots.length - 1];
		return new ServiceHours(first.startTime().toString(), last.endTimeExclusive().toString(), last.endsNextDay());
	}

	private static double roundCoordinate(double degrees) {
		double scale = Math.pow(10, COORDINATE_DECIMALS);
		return Math.round(degrees * scale) / scale;
	}

	private static List<String> values(Collection<? extends Enum<?>> constants) {
		return constants.stream().map(ApiEnums::toValue).toList();
	}

	private static List<String> withAll(Collection<? extends Enum<?>> constants) {
		List<String> values = new ArrayList<>(values(constants));
		values.add(ALL);
		return values;
	}
}
