package com.odyssey.recommendation.v2;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Candidate;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.cellinsight.repository.CellInsightRepository.Place;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class V2RecommendationService {
	static final int RADIUS_METERS = 300;
	static final int MAX_RESULTS = 30;
	static final double MIN_VISITOR_PERCENTILE = 75;
	private final CellInsightRepository repository;
	private final V2DatasetResolver datasets;
	public V2RecommendationService(CellInsightRepository repository, V2DatasetResolver datasets) {
		this.repository = repository;
		this.datasets = datasets;
	}
	public V2RecommendationResponse recommend(V2Query query) {
		Dataset batch = datasets.require(query.datasetId());
		List<Located> selected = repository.candidates(query.datasetId(), query.weekday(), query.slot(),
				MIN_VISITOR_PERCENTILE)
			.stream().filter(c -> matches(c, query))
			.map(c -> new Located(c, distance(query.lat(), query.lng(), c.place().lat(), c.place().lng())))
			.filter(c -> c.distance() <= RADIUS_METERS)
			.sorted(Comparator.comparingInt(Located::band).reversed()
				.thenComparingDouble(Located::distance).thenComparing(c -> c.row().mgmtNo()))
			.limit(MAX_RESULTS).toList();
		List<Item> items = selected.stream().map(c -> {
			Place s = c.row().place();
			return new Item(s.id(), s.name(), s.cellId(), s.kind(), s.lat(), s.lng(),
				Math.round(c.distance() * 10) / 10.0, s.googlePlaceId());
		}).toList();
		V2Context context = new V2Context(batch.id(), batch.referenceMonth(), query.weekday(), query.slot());
		return new V2RecommendationResponse(items.isEmpty() ? "NO_MATCHES" : "OK", items.size(),
			context, items);
	}

	private static boolean matches(Candidate c, V2Query q) {
		Place s = c.place();
		return c.percentile() >= MIN_VISITOR_PERCENTILE
			&& !("food".equals(q.category()) && s.kind().equals("cafe"))
			&& !("cafe".equals(q.category()) && !s.kind().equals("cafe"))
			&& (q.kind() == null || q.kind().equals(s.kind()))
			&& s.name().toLowerCase(Locale.ROOT).contains(q.q().toLowerCase(Locale.ROOT));
	}

	private record Located(Candidate row, double distance) {
		int band() { return Math.min(4, (int)Math.floor(row.percentile() / 20)); }
	}

	static double distance(double lat, double lng, double otherLat, double otherLng) {
		double a = Math.toRadians(otherLat - lat), b = Math.toRadians(otherLng - lng);
		double h = Math.pow(Math.sin(a / 2), 2) + Math.cos(Math.toRadians(lat))
			* Math.cos(Math.toRadians(otherLat)) * Math.pow(Math.sin(b / 2), 2);
		return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h))));
	}
}
