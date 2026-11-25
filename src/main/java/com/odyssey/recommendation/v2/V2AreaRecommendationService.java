package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.AreaRecommendationRepository;
import com.odyssey.cellcondition.repository.AreaRecommendationRepository.Page;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.V2AreaRecommendationResponse.Item;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;
import com.odyssey.store.StoreNames.Language;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class V2AreaRecommendationService {

	private final AreaRecommendationRepository repository;
	private final V2DatasetResolver datasets;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public V2AreaRecommendationResponse recommend(String datasetId, Weekday weekday, TimeSlot slot,
		Area area, Set<StoreKind> kinds, int page, int size, Language language) {
		Dataset dataset = datasets.require(datasetId);
		long offset = (long) page * size;
		Page found = repository.findPage(dataset.id(), weekday, slot, area, kinds,
			V2RecommendationSelector.MIN_VISITOR_PERCENTILE, offset, size);
		List<Item> items = found.items().stream().map(row -> {
			Place place = row.place();
			return new Item(place.id(), place.names().display(place.name(), language),
				Long.toString(place.cellId()), row.area(), place.kind(), place.lat(), place.lng(), place.googlePlaceId());
		}).toList();
		V2Context context = new V2Context(dataset.id(), dataset.referenceMonth(), weekday, slot);
		return new V2AreaRecommendationResponse(items.isEmpty() ? V2RecommendationResponse.NO_MATCHES
			: V2RecommendationResponse.OK, found.total(), page, size, offset + items.size() < found.total(),
			context, items);
	}
}
