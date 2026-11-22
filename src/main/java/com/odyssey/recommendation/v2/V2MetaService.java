package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.api.ApiEnums;
import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.StoreKind;

/** 첫 화면에서 고를 수 있는 선택지를 만든다. 선택지의 순서는 프론트 목(meta.json)과의 약속이다. */
@Service
@Transactional(readOnly = true)
public class V2MetaService {

	private static final String ALL = "all";

	// 선언 순서(CAFE 가 첫째)와 달리 화면은 한식, 술집, 기타, 카페, 쇼핑 순으로 보여 준다
	private static final List<StoreKind> KIND_ORDER =
		List.of(StoreKind.KOREAN, StoreKind.BAR, StoreKind.OTHER, StoreKind.CAFE, StoreKind.SHOP);

	private final V2DatasetResolver datasets;

	public V2MetaService(V2DatasetResolver datasets) {
		this.datasets = datasets;
	}

	/**
	 * @return 지원하는 공개 배치(기준월 내림차순)와 고정 선택지. 배치가 없어도 고정 선택지는 준다
	 */
	public V2MetaResponse meta() {
		List<DatasetOption> options = datasets.findSupported().stream()
			.map(dataset -> new DatasetOption(
				dataset.id(), dataset.referenceMonth(), dataset.population()))
			.toList();
		return new V2MetaResponse(
			options,
			values(Arrays.asList(Weekday.values())),
			values(Arrays.asList(TimeSlot.values())),
			withAll(Arrays.asList(Category.values())),
			withAll(KIND_ORDER));
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
