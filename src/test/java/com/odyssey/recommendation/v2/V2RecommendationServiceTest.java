package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Candidate;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.cellinsight.repository.CellInsightRepository.Place;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;

/**
 * {@link V2RecommendationService} 의 추천 규칙을 확인한다. 리포지토리는 목으로 대신한다.
 *
 * <p>후보의 위치는 질의 위치(0, 0)에서 "정확히 N미터" 떨어진 점으로 만든다. 같은 경도(0)에서 위도만 바꾸면
 * 거리가 N미터가 되므로 테스트에서 거리를 직접 정할 수 있다.
 */
class V2RecommendationServiceTest {

	private static final String DS = "nov";
	private static final String POLICY = "visit75-band300-v2-split";
	private static final String REASON = "visit75-no-worker-v2-split";
	private static final String PROFILE = "visit-only-relative100-step5-v2-split";

	private CellInsightRepository repo;
	private V2RecommendationService service;

	@BeforeEach
	void setup() {
		repo = mock(CellInsightRepository.class);
		when(repo.datasets()).thenReturn(List.of(
			new Dataset(DS, "2025-11", "DOMESTIC_20_39", POLICY, REASON, PROFILE)));
		service = new V2RecommendationService(repo, new V2DatasetResolver(repo));
	}

	private static Candidate candidate(long id, String name, String kind, double percentile, double meters) {
		double lat = meters / 6371000 * 180 / Math.PI;
		Place place = new Place(id, name, "10", kind, lat, 0, null, "place-" + id);
		return new Candidate(place, "m" + id, percentile);
	}

	private static Candidate candidate(long id, double percentile, double meters) {
		return candidate(id, "장소" + id, "korean", percentile, meters);
	}

	private static V2Query query(String category, String kind, String q) {
		return new V2Query(DS, "mon", "dinner", 0, 0, category, kind, q);
	}

	private void candidates(Candidate... rows) {
		when(repo.candidates(DS, "mon", "dinner")).thenReturn(List.of(rows));
	}

	private static List<Long> ids(V2RecommendationResponse response) {
		return response.items().stream().map(Item::id).toList();
	}

	// ------------------------------------------------------------ 응답 모양

	@Test
	@DisplayName("후보가 하나도 없다 → NO_MATCHES, 개수 0, 조회 조건은 그대로 돌려준다")
	void noMatches() {
		// given
		candidates();

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(response.status()).isEqualTo("NO_MATCHES");
		assertThat(response.total()).isZero();
		assertThat(response.items()).isEmpty();
		assertThat(response.context()).isEqualTo(new V2Context(DS, "2025-11", "mon", "dinner"));
	}

	@Test
	@DisplayName("후보가 있다 → OK, 개수는 목록 길이와 같고 매장 값은 그대로 옮긴다")
	void okWithItems() {
		// given
		candidates(candidate(7, "공평식당", "korean", 90, 50));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(response.status()).isEqualTo("OK");
		assertThat(response.total()).isEqualTo(1);
		Item item = response.items().getFirst();
		assertThat(item.id()).isEqualTo(7);
		assertThat(item.name()).isEqualTo("공평식당");
		assertThat(item.cellId()).isEqualTo("10");
		assertThat(item.kind()).isEqualTo("korean");
		assertThat(item.googlePlaceId()).isEqualTo("place-7");
		assertThat(item.distanceMeters()).isCloseTo(50.0, within(0.1));
	}

	@Test
	@DisplayName("거리가 소수로 떨어진다 → 소수 첫째 자리로 반올림해 준다")
	void distanceRoundedToOneDecimal() {
		// given
		candidates(candidate(1, 90, 123.456));

		// when
		double meters = service.recommend(query("all", "all", "")).items().getFirst().distanceMeters();

		// then: 123.5 근처이고 소수 둘째 자리 이하가 없다
		assertThat(meters).isCloseTo(123.5, within(0.1));
		assertThat(meters * 10).isCloseTo(Math.rint(meters * 10), within(1e-6));
	}

	// ------------------------------------------------------------ 반경 · 개수

	@Test
	@DisplayName("반경 300m 안(299m)은 넣고 밖(301m)은 뺀다")
	void radius() {
		// given
		candidates(candidate(1, 90, 299), candidate(2, 90, 301));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(1L);
	}

	@Test
	@DisplayName("후보가 35개다 → 30개까지만 주고 개수도 30이다")
	void maxResults() {
		// given
		candidates(IntStream.rangeClosed(1, 35).mapToObj(i -> candidate(i, 90, i)).toArray(Candidate[]::new));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(response.items()).hasSize(30);
		assertThat(response.total()).isEqualTo(30);
	}

	// ------------------------------------------------------------ 정렬

	@Test
	@DisplayName("방문 점수 구간이 높으면 더 멀어도 먼저 온다 (79.99 보다 80 이 앞)")
	void bandBoundaryAtEighty() {
		// given: 79.99 는 가까이, 80 은 멀리
		candidates(candidate(1, 79.99, 10), candidate(2, 80, 250));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("점수가 100 이어도 80점대와 같은 구간이다 → 거리순으로 준다")
	void hundredIsInTopBand() {
		// given
		candidates(candidate(1, 100, 200), candidate(2, 80, 100));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("구간과 거리가 같다 → 관리번호 순으로 정해 항상 같은 순서로 준다")
	void tieBrokenByManagementNumber() {
		// given: 일부러 거꾸로 준다
		candidates(candidate(2, 90, 50), candidate(1, 90, 50));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("점수가 75 미만인 후보 → 목록에서 뺀다")
	void belowThresholdIsDropped() {
		// given
		candidates(candidate(1, 74.99, 10), candidate(2, 75, 10));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(2L);
	}

	// ------------------------------------------------------------ 필터

	@Test
	@DisplayName("카테고리가 food 다 → 카페는 빼고, cafe 면 카페만 남긴다")
	void categoryFilter() {
		// given
		candidates(candidate(1, "식당", "korean", 90, 10), candidate(2, "카페", "cafe", 90, 20),
			candidate(3, "술집", "bar", 90, 30));

		// when / then
		assertThat(ids(service.recommend(query("food", "all", "")))).containsExactly(1L, 3L);
		assertThat(ids(service.recommend(query("cafe", "all", "")))).containsExactly(2L);
		assertThat(ids(service.recommend(query("all", "all", "")))).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("종류를 주었다 → 그 종류만 남긴다")
	void kindFilter() {
		// given
		candidates(candidate(1, "한식집", "korean", 90, 10), candidate(2, "술집", "bar", 90, 20));

		// when
		V2RecommendationResponse response = service.recommend(query("food", "bar", ""));

		// then
		assertThat(ids(response)).containsExactly(2L);
	}

	@Test
	@DisplayName("검색어를 주었다 → 이름에 포함된 매장만 남기고 대소문자는 구분하지 않는다")
	void nameSearch() {
		// given
		candidates(candidate(1, "Cafe Onion", "cafe", 90, 10), candidate(2, "스타벅스", "cafe", 90, 20));

		// when / then
		assertThat(ids(service.recommend(query("all", "all", "onion")))).containsExactly(1L);
		assertThat(ids(service.recommend(query("all", "all", "스타")))).containsExactly(2L);
		assertThat(ids(service.recommend(query("all", "all", "없는이름")))).isEmpty();
	}

	@Test
	@DisplayName("검색어에 맞는 매장이 없다 → NO_MATCHES 로 준다")
	void nameSearchNoMatchStatus() {
		// given
		candidates(candidate(1, "Cafe Onion", "cafe", 90, 10));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", "없는이름"));

		// then
		assertThat(response.status()).isEqualTo("NO_MATCHES");
	}

	// ------------------------------------------------------------ 데이터셋

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND, 후보는 조회하지 않는다")
	void unknownDataset() {
		// given
		V2Query unknown = new V2Query("unknown", "mon", "dinner", 0, 0, "all", "all", "");

		// when / then
		assertThatThrownBy(() -> service.recommend(unknown)).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND));
		verify(repo, never()).candidates(anyString(), anyString(), anyString());
	}

	@ParameterizedTest
	@CsvSource({
		"other-policy," + REASON + "," + PROFILE,
		POLICY + ",other-reason," + PROFILE,
		POLICY + "," + REASON + ",other-profile"})
	@DisplayName("서버가 모르는 정책 · 이유 · 프로필 버전의 데이터셋 → DATA_UNAVAILABLE")
	void unsupportedVersions(String policy, String reason, String profile) {
		// given
		when(repo.datasets()).thenReturn(List.of(
			new Dataset(DS, "2025-11", "DOMESTIC_20_39", policy, reason, profile)));

		// when / then
		assertThatThrownBy(() -> service.recommend(query("all", "all", ""))).isInstanceOfSatisfying(
			BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.DATA_UNAVAILABLE));
		verify(repo, never()).candidates(anyString(), anyString(), anyString());
	}

	// ------------------------------------------------------------ 거리 계산

	@Test
	@DisplayName("같은 지점 → 0m, 위도 1도 차이 → 약 111.2km")
	void distanceFormula() {
		// when / then
		assertThat(V2RecommendationService.distance(37.5, 127.0, 37.5, 127.0)).isZero();
		assertThat(V2RecommendationService.distance(0, 0, 1, 0)).isCloseTo(111_195.0, within(5.0));
	}
}
