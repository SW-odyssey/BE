package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.recommendation.v2.V2RecommendationResponse.Item;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/**
 * {@link V2RecommendationService} 를 확인한다. 선별 규칙은 selector 테스트가 보므로 여기서는
 * 배치 확인, 조회 조건 전달, 응답 조립만 본다. 리포지토리와 리졸버는 목으로 대신한다.
 */
class V2RecommendationServiceTest {

	private static final double ORIGIN_LAT = 37.5655;
	private static final double ORIGIN_LNG = 126.9905;
	/** 같은 경도에서 위도 1도가 가리키는 거리. */
	private static final double METERS_PER_DEGREE = Math.toRadians(1) * 6_371_000;
	private static final Dataset DATASET =
		new Dataset("nov", "2025-11", "DOMESTIC_20_39", "policy", "reason", "profile");

	private CellConditionRepository repository;
	private V2DatasetResolver datasets;
	private V2RecommendationService service;

	@BeforeEach
	void setUp() {
		repository = mock(CellConditionRepository.class);
		datasets = mock(V2DatasetResolver.class);
		service = new V2RecommendationService(repository, datasets);
		given(datasets.require("nov")).willReturn(DATASET);
	}

	private static V2RecommendationQuery query() {
		return new V2RecommendationQuery("nov", Weekday.SUN, TimeSlot.DINNER,
			new GeoPoint(ORIGIN_LAT, ORIGIN_LNG), null, null, "");
	}

	private static Candidate candidate(long id, double meters, double percentile) {
		Place place = new Place(id, "매장" + id, 81_000_000L + id, StoreKind.BAR,
			ORIGIN_LAT + meters / METERS_PER_DEGREE, ORIGIN_LNG, null, "place-" + id);
		return new Candidate(place, "mgmt-" + id, percentile);
	}

	private void givenCandidates(Candidate... rows) {
		given(repository.candidates(anyString(), any(Weekday.class), any(TimeSlot.class),
			anyDouble())).willReturn(List.of(rows));
	}

	@Test
	@DisplayName("후보가 하나도 없다 → NO_MATCHES 와 빈 목록을 준다")
	void noMatches() {
		// given
		givenCandidates();

		// when
		V2RecommendationResponse response = service.recommend(query());

		// then
		assertThat(response.status()).isEqualTo("NO_MATCHES");
		assertThat(response.total()).isZero();
		assertThat(response.items()).isEmpty();
	}

	@Test
	@DisplayName("조회하면 → 분석 배치의 ID 와 기준월, 요청한 요일 · 시간대를 context 로 돌려준다")
	void echoesContext() {
		// given
		givenCandidates();

		// when
		V2RecommendationResponse response = service.recommend(query());

		// then
		assertThat(response.context())
			.isEqualTo(new V2Context("nov", "2025-11", Weekday.SUN, TimeSlot.DINNER));
	}

	@Test
	@DisplayName("후보를 조회한다 → 요청한 배치 · 요일 · 시간대와 방문 백분위 하한 75 를 넘긴다")
	void passesConditionAndMinPercentile() {
		// given
		givenCandidates();

		// when
		service.recommend(query());

		// then
		then(repository).should().candidates("nov", Weekday.SUN, TimeSlot.DINNER, 75.0);
	}

	@Test
	@DisplayName("후보가 있다 → OK 와 목록 길이만큼의 개수를 주고 선별한 순서를 그대로 쓴다")
	void okWithItemsInSelectedOrder() {
		// given: 백분위가 높은 2번이 더 멀어도 앞선다
		givenCandidates(candidate(1, 30, 81), candidate(2, 200, 98));

		// when
		V2RecommendationResponse response = service.recommend(query());

		// then
		assertThat(response.status()).isEqualTo("OK");
		assertThat(response.total()).isEqualTo(2);
		assertThat(response.items()).extracting(Item::id).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("후보가 있다 → 칸 번호는 문자열로, 나머지 매장 값은 그대로 옮기고 place ID 도 준다")
	void mapsItemFields() {
		// given
		givenCandidates(candidate(7, 100, 90));

		// when
		Item item = service.recommend(query()).items().getFirst();

		// then
		assertThat(item.id()).isEqualTo(7);
		assertThat(item.name()).isEqualTo("매장7");
		assertThat(item.cellId()).isEqualTo("81000007");
		assertThat(item.kind()).isEqualTo(StoreKind.BAR);
		assertThat(item.googlePlaceId()).isEqualTo("place-7");
	}

	@Test
	@DisplayName("거리가 소수로 떨어진다 → 소수 첫째 자리로 반올림해 준다")
	void distanceRoundedToOneDecimal() {
		// given
		givenCandidates(candidate(1, 12.34, 90), candidate(2, 12.36, 80));

		// when
		List<Item> items = service.recommend(query()).items();

		// then
		assertThat(items).extracting(Item::distanceMeters).containsExactly(12.3, 12.4);
	}

	@Test
	@DisplayName("공개되지 않은 datasetId → DATASET_NOT_FOUND 이고 후보는 조회하지 않는다")
	void unknownDatasetSkipsCandidates() {
		// given
		given(datasets.require("nov")).willThrow(new BusinessException(
			V2Error.DATASET_NOT_FOUND, "공개된 분석 배치를 찾을 수 없습니다."));

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.recommend(query()));

		// then
		assertThat(exception.getErrorType()).isEqualTo(V2Error.DATASET_NOT_FOUND);
		then(repository).should(never()).candidates(anyString(), any(Weekday.class),
			any(TimeSlot.class), anyDouble());
	}

	@Test
	@DisplayName("서버가 모르는 정책의 배치 → DATA_UNAVAILABLE 이고 후보는 조회하지 않는다")
	void unsupportedDatasetSkipsCandidates() {
		// given
		given(datasets.require("nov")).willThrow(new BusinessException(
			CommonErrorCode.DATA_UNAVAILABLE, "서버가 지원하지 않는 추천 정책입니다."));

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.recommend(query()));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.DATA_UNAVAILABLE);
		then(repository).should(never()).candidates(anyString(), any(Weekday.class),
			any(TimeSlot.class), anyDouble());
	}
}
