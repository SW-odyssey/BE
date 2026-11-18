package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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
		given(repo.datasets()).willReturn(List.of(
			new Dataset(DS, "2025-11", "DOMESTIC_20_39", POLICY, REASON, PROFILE)));
		service = new V2RecommendationService(repo, new V2DatasetResolver(repo));
	}

	private static Candidate candidate(long id, String name, String kind, double percentile,
		double meters) {
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

	private static V2Query anyQuery() {
		return query("all", "all", "");
	}

	private void givenCandidates(Candidate... rows) {
		given(repo.candidates(DS, "mon", "dinner")).willReturn(List.of(rows));
	}

	private static List<Long> ids(V2RecommendationResponse response) {
		return response.items().stream().map(Item::id).toList();
	}

	// ------------------------------------------------------------ 응답 모양

	@Test
	@DisplayName("후보가 하나도 없다 → NO_MATCHES 와 빈 목록을 준다")
	void noMatches() {
		// given
		givenCandidates();

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(response.status()).isEqualTo("NO_MATCHES");
		assertThat(response.total()).isZero();
		assertThat(response.items()).isEmpty();
	}

	@Test
	@DisplayName("조회하면 → 분석 배치와 요일 · 시간대를 조회 조건으로 돌려준다")
	void echoesContext() {
		// given
		givenCandidates();

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(response.context()).isEqualTo(new V2Context(DS, "2025-11", "mon", "dinner"));
	}

	@Test
	@DisplayName("후보가 있다 → OK 와 목록 길이만큼의 개수를 준다")
	void okWithItems() {
		// given
		givenCandidates(candidate(1, 90, 10), candidate(2, 90, 20));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(response.status()).isEqualTo("OK");
		assertThat(response.total()).isEqualTo(2);
	}

	@Test
	@DisplayName("후보가 있다 → 매장 값은 그대로 옮기고 place ID 도 함께 준다")
	void mapsItemFields() {
		// given
		givenCandidates(candidate(7, "공평식당", "korean", 90, 50));

		// when
		Item item = service.recommend(anyQuery()).items().getFirst();

		// then
		assertThat(item).extracting(Item::id, Item::name, Item::cellId, Item::kind,
			Item::googlePlaceId).containsExactly(7L, "공평식당", "10", "korean", "place-7");
		assertThat(item.distanceMeters()).isCloseTo(50.0, within(0.1));
	}

	@Test
	@DisplayName("거리가 소수로 떨어진다 → 소수 첫째 자리로 반올림해 준다")
	void distanceRoundedToOneDecimal() {
		// given
		givenCandidates(candidate(1, 90, 123.456));

		// when
		double meters = service.recommend(anyQuery()).items().getFirst().distanceMeters();

		// then: 123.5 근처이고 소수 둘째 자리 이하가 없다
		assertThat(meters).isCloseTo(123.5, within(0.1));
		assertThat(meters * 10).isCloseTo(Math.rint(meters * 10), within(1e-6));
	}

	// ------------------------------------------------------------ 반경 · 개수

	@Test
	@DisplayName("후보가 반경 300m 안(299m)에 있다 → 목록에 넣는다")
	void insideRadius() {
		// given
		givenCandidates(candidate(1, 90, 299));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).containsExactly(1L);
	}

	@Test
	@DisplayName("후보가 반경 300m 밖(301m)에 있다 → 목록에서 뺀다")
	void outsideRadius() {
		// given
		givenCandidates(candidate(1, 90, 301));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(response.items()).isEmpty();
	}

	@Test
	@DisplayName("후보가 35개다 → 30개까지만 주고 개수도 30이다")
	void maxResults() {
		// given
		givenCandidates(IntStream.rangeClosed(1, 35).mapToObj(i -> candidate(i, 90, i))
			.toArray(Candidate[]::new));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(response.items()).hasSize(30);
		assertThat(response.total()).isEqualTo(30);
	}

	@Test
	@DisplayName("후보가 31개다 → 가까운 순서대로 30개를 고르고 가장 먼 1개를 뺀다")
	void maxResultsKeepsNearest() {
		// given: 먼 것부터 거꾸로 준다
		givenCandidates(IntStream.iterate(31, i -> i > 0, i -> i - 1)
			.mapToObj(i -> candidate(i, 90, i)).toArray(Candidate[]::new));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).doesNotContain(31L).hasSize(30).first().isEqualTo(1L);
	}

	// ------------------------------------------------------------ 정렬

	@Test
	@DisplayName("방문 점수 구간이 높다 → 더 멀어도 먼저 온다 (79.99 보다 80 이 앞)")
	void higherBandFirst() {
		// given: 79.99 는 가까이, 80 은 멀리
		givenCandidates(candidate(1, 79.99, 10), candidate(2, 80, 250));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("점수가 100 이어도 80점대와 같은 구간이다 → 거리순으로 준다")
	void hundredIsInTopBand() {
		// given
		givenCandidates(candidate(1, 100, 200), candidate(2, 80, 100));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("구간과 거리가 같다 → 관리번호 순으로 정해 항상 같은 순서로 준다")
	void tieBrokenByManagementNumber() {
		// given: 일부러 거꾸로 준다
		givenCandidates(candidate(2, 90, 50), candidate(1, 90, 50));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("점수가 75 미만인 후보 → 목록에서 뺀다")
	void belowThresholdIsDropped() {
		// given
		givenCandidates(candidate(1, 74.99, 10), candidate(2, 75, 10));

		// when
		V2RecommendationResponse response = service.recommend(anyQuery());

		// then
		assertThat(ids(response)).containsExactly(2L);
	}

	// ------------------------------------------------------------ 필터

	private void givenMixedKinds() {
		givenCandidates(candidate(1, "식당", "korean", 90, 10),
			candidate(2, "카페", "cafe", 90, 20), candidate(3, "술집", "bar", 90, 30));
	}

	@Test
	@DisplayName("카테고리가 food 다 → 카페는 빼고 식당과 술집만 준다")
	void foodCategory() {
		// given
		givenMixedKinds();

		// when
		V2RecommendationResponse response = service.recommend(query("food", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(1L, 3L);
	}

	@Test
	@DisplayName("카테고리가 cafe 다 → 카페만 준다")
	void cafeCategory() {
		// given
		givenMixedKinds();

		// when
		V2RecommendationResponse response = service.recommend(query("cafe", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(2L);
	}

	@Test
	@DisplayName("카테고리가 all 이다 → 종류와 상관없이 모두 준다")
	void allCategory() {
		// given
		givenMixedKinds();

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", ""));

		// then
		assertThat(ids(response)).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("종류를 주었다 → 그 종류만 남긴다")
	void kindFilter() {
		// given
		givenMixedKinds();

		// when
		V2RecommendationResponse response = service.recommend(query("food", "bar", ""));

		// then
		assertThat(ids(response)).containsExactly(3L);
	}

	@ParameterizedTest
	@CsvSource({"onion,1", "ONION,1", "스타,2"})
	@DisplayName("검색어를 주었다 → 이름에 포함된 매장만 남기고 대소문자는 구분하지 않는다")
	void nameSearch(String text, long expectedId) {
		// given
		givenCandidates(candidate(1, "Cafe Onion", "cafe", 90, 10),
			candidate(2, "스타벅스", "cafe", 90, 20));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", text));

		// then
		assertThat(ids(response)).containsExactly(expectedId);
	}

	@Test
	@DisplayName("검색어에 맞는 매장이 없다 → NO_MATCHES 와 빈 목록을 준다")
	void nameSearchWithoutMatch() {
		// given
		givenCandidates(candidate(1, "Cafe Onion", "cafe", 90, 10));

		// when
		V2RecommendationResponse response = service.recommend(query("all", "all", "없는이름"));

		// then
		assertThat(response.status()).isEqualTo("NO_MATCHES");
		assertThat(response.items()).isEmpty();
	}

	// ------------------------------------------------------------ 데이터셋

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND")
	void unknownDataset() {
		// given
		V2Query unknown = new V2Query("unknown", "mon", "dinner", 0, 0, "all", "all", "");

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.recommend(unknown));

		// then
		assertThat(exception.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
	}

	@Test
	@DisplayName("공개되지 않은 datasetId → 후보는 조회하지 않는다")
	void unknownDatasetSkipsCandidates() {
		// given
		V2Query unknown = new V2Query("unknown", "mon", "dinner", 0, 0, "all", "all", "");

		// when
		catchThrowableOfType(BusinessException.class, () -> service.recommend(unknown));

		// then
		then(repo).should(never()).candidates(anyString(), anyString(), anyString());
	}

	@ParameterizedTest
	@CsvSource({
		"other-policy," + REASON + "," + PROFILE,
		POLICY + ",other-reason," + PROFILE,
		POLICY + "," + REASON + ",other-profile"})
	@DisplayName("서버가 모르는 정책 · 이유 · 프로필 버전의 데이터셋 → DATA_UNAVAILABLE")
	void unsupportedVersions(String policy, String reason, String profile) {
		// given
		given(repo.datasets()).willReturn(List.of(
			new Dataset(DS, "2025-11", "DOMESTIC_20_39", policy, reason, profile)));

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.recommend(anyQuery()));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.DATA_UNAVAILABLE);
	}

	// ------------------------------------------------------------ 거리 계산

	@Test
	@DisplayName("같은 지점이다 → 거리는 0m 다")
	void samePointDistance() {
		// when
		double meters = V2RecommendationService.distance(37.5, 127.0, 37.5, 127.0);

		// then
		assertThat(meters).isZero();
	}

	@Test
	@DisplayName("위도가 1도 차이 난다 → 약 111.2km 다")
	void oneDegreeOfLatitude() {
		// when
		double meters = V2RecommendationService.distance(0, 0, 1, 0);

		// then
		assertThat(meters).isCloseTo(111_195.0, within(5.0));
	}
}
