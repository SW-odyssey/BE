package com.odyssey.recommendation.v2;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;

@Service
@Transactional(readOnly = true)
public class V2MetaService {
	private final CellInsightRepository repository;
	public V2MetaService(CellInsightRepository repository) { this.repository = repository; }
	public V2MetaResponse meta() {
		return new V2MetaResponse(repository.datasets().stream().filter(V2DatasetResolver::supported).map(d ->
			new V2MetaResponse.DatasetOption(d.id(), d.referenceMonth(), d.population())).toList(),
			V2Query.DAYS, V2Query.SLOTS, List.of("food", "cafe", "all"),
			List.of("korean", "bar", "other", "cafe", "all"));
	}
}
