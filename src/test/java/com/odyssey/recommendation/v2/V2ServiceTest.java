package com.odyssey.recommendation.v2;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;
import com.odyssey.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class V2ServiceTest {
	CellInsightRepository repo;
	V2RecommendationService service;
	V2CellReasonService reasons;
	V2VisitPatternService profiles;
	@BeforeEach void setup() {
		repo = mock(CellInsightRepository.class);
		when(repo.datasets()).thenReturn(List.of(new Dataset("nov", "2025-11", "DOMESTIC_20_39",
			"visit75-band300-v2-split", "visit75-no-worker-v2-split", "visit-only-relative100-step5-v2-split")));
		var datasets = new V2DatasetResolver(repo);
		service = new V2RecommendationService(repo, datasets);
		reasons = new V2CellReasonService(repo, datasets);
		profiles = new V2VisitPatternService(repo, datasets);
	}

	static Candidate candidate(long id, double pct, double meters) {
		Place place = new Place(id, "장소" + id, "10", "korean", meters / 6371000 * 180 / Math.PI,
			0, null, "place" + id);
		return new Candidate(place, "m" + id, pct);
	}
	static V2Query query() {
		return new V2Query("nov", "mon", "dinner", 0, 0, "food", "all", "");
	}

	@Test void bandPriorityThenDistanceDoesNotUseExactPercentileForTies() {
		when(repo.candidates("nov", "mon", "dinner", 75.0)).thenReturn(List.of(
			candidate(1, 79, 10), candidate(2, 99, 200), candidate(3, 80, 100)));
		assertThat(service.recommend(query()).items()).extracting(V2RecommendationResponse.Item::id)
			.containsExactly(3L, 2L, 1L);
	}

	@Test void lowEvidenceAndOutsideRadiusDoNotBackfillThirty() {
		when(repo.candidates("nov", "mon", "dinner", 75.0)).thenReturn(List.of(
			candidate(1, 74.999, 10), candidate(2, 100, 301), candidate(3, 75, 100)));
		assertThat(service.recommend(query()).items()).extracting(V2RecommendationResponse.Item::id).containsExactly(3L);
	}

	@Test void thirtyOneCandidatesReturnThirtyInDistanceOrder() {
		// given
		when(repo.candidates("nov", "mon", "dinner", 75.0)).thenReturn(
			java.util.stream.IntStream.iterate(31, i -> i > 0, i -> i - 1)
				.mapToObj(i -> candidate(i, 90, i)).toList());
		// when
		var result = service.recommend(query());
		// then
		assertThat(result.items()).extracting(V2RecommendationResponse.Item::id)
			.containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1, 30).boxed().toList());
	}

	@Test void reasonsDistinguishIncompleteLowEvidenceAndUnknownCell() {
		when(repo.reasons("nov", "mon", "dinner", List.of(1L, 2L, 3L))).thenReturn(List.of(
			new ReasonRow("1", "INCOMPLETE", null, null),
			new ReasonRow("2", "AVAILABLE", null, null)));
		assertThat(reasons.reasons("nov", "mon", "dinner", List.of(1L, 2L, 3L)).reasonsByCellId().values())
			.extracting(V2ReasonsResponse.CellReason::status)
			.containsExactly("INCOMPLETE", "NO_RECOMMENDATION_EVIDENCE", "CELL_NOT_IN_DATASET");
	}

	@Test void missingBatchNeverFallsBackToAnotherMonth() {
		assertThatThrownBy(() -> reasons.reasons("unknown", "mon", "dinner", List.of(1L)))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND));
		verify(repo, never()).reasons(anyString(), anyString(), anyString(), anyList());
	}

	@Test void reasonLookupSupportsOneCellAndDoesNotExposeWorkerStatements() {
		// given
		when(repo.reasons("nov", "mon", "dinner", List.of(1L))).thenReturn(List.of(
			new ReasonRow("1", "AVAILABLE", 1, "VISIT_RELATIVE_HIGH"),
			new ReasonRow("1", "AVAILABLE", 2, "WORKER_DISTINCT")));
		// when
		var result = reasons.reasons("nov", "mon", "dinner", List.of(1L));
		// then
		assertThat(result.reasonsByCellId()).containsOnlyKeys("1");
		assertThat(result.reasonsByCellId().get("1").reasons())
			.extracting(V2ReasonsResponse.Reason::code).containsExactly("VISIT_RELATIVE_HIGH");
	}

	@Test void incompleteProfileHasNoInventedBars() {
		when(repo.profile("nov", "wed", 10)).thenReturn(Optional.of(new ProfileRow("INCOMPLETE_TIME_SLOTS", List.of())));
		var response = profiles.profile("nov", "wed", 10);
		assertThat(response.graph()).isNull();
		assertThat(response.status()).isEqualTo("INCOMPLETE_TIME_SLOTS");
	}

	@Test void normalizedBarsReconstructPublicGraphAndCaption() {
		when(repo.profile("nov", "sat", 10)).thenReturn(Optional.of(new ProfileRow("AVAILABLE", List.of(
			new VisitBar("lunch", 25), new VisitBar("afternoon", 40),
			new VisitBar("dinner", 100), new VisitBar("late_night", 10)))));
		var graph = profiles.profile("nov", "sat", 10).graph();
		assertThat(graph.highlightSlot()).isEqualTo("dinner");

		assertThat(graph.bars()).extracting(V2VisitPatternResponse.Bar::slot).containsExactly("lunch", "afternoon", "dinner", "late_night");
	}

	@Test void reasonCodesUseNormalizedContextWithoutServerCopy() {
        when(repo.reasons("nov", "sat", "dinner", List.of(1L)))
            .thenReturn(List.of(new ReasonRow("1", "AVAILABLE", 1, "VISIT_RELATIVE_HIGH")));
        var result = reasons.reasons("nov", "SAT", "DINNER", List.of(1L));
        assertThat(result.context().weekday()).isEqualTo("sat");
        assertThat(result.reasonsByCellId().get("1").reasons().getFirst().code()).isEqualTo("VISIT_RELATIVE_HIGH");
    }
}
