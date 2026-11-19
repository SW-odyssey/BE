package com.odyssey.cellcondition.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellcondition.repository.CellConditionRepository.Candidate;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;
import com.odyssey.support.SplitRecommendationFixture;

/** {@link CellConditionRepository} 의 후보 조건과 섞임 방지를 실제 Postgres 에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellConditionRepositoryTest {

	private static final String DS = "ds-condition";
	private static final String MON = "mon";
	private static final String LUNCH = "lunch";
	private static final double MIN_PERCENTILE = 75.0;

	@Autowired
	private CellConditionRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void setUp() {
		db = new SplitRecommendationFixture(jdbc);
		db.cell(101);
		db.cell(102);
		db.cell(103);
		db.dataset(DS, "2026-10-01", "2026-10-31T00:00:00Z", true);
	}

	private List<Candidate> candidates(double minPercentile) {
		return repository.candidates(DS, Weekday.MON, TimeSlot.LUNCH, minPercentile);
	}

	@Test
	@DisplayName("방문 백분위가 하한과 같다 → 후보에 넣고, 하한 미만은 뺀다")
	void percentileBoundary() {
		// given: 경계값 75.0 은 포함, 74.99 는 제외
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 75.0);
		db.condition(DS, 102, MON, LUNCH, "AVAILABLE", 74.99);
		db.store(1, 101, "경계 포함", "KOREAN", "1995-05-20", "place-1", true);
		db.store(2, 102, "경계 미만", "KOREAN", "1995-05-20", "place-2", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("경계 포함");
	}

	@Test
	@DisplayName("하한으로 90 을 넘긴다 → 75 이상이어도 90 미만은 뺀다")
	void usesGivenMinPercentile() {
		// given: 둘 다 75 이상이지만 90 기준으로는 한 곳만 남는다
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 102, MON, LUNCH, "AVAILABLE", 80.0);
		db.store(1, 101, "90점 매장", "KOREAN", null, "place-1", true);
		db.store(2, 102, "80점 매장", "KOREAN", null, "place-2", true);

		// when
		List<Candidate> candidates = candidates(90.0);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("90점 매장");
	}

	@Test
	@DisplayName("품질 상태가 AVAILABLE 이 아니다 → 후보에서 뺀다")
	void excludesNotAvailable() {
		// given: 관측되지 않은 칸은 백분위가 없다 (CHECK 제약)
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 102, MON, LUNCH, "NOT_OBSERVED", null);
		db.store(1, 101, "추천 칸", "CAFE", null, "place-1", true);
		db.store(2, 102, "관측 안 된 칸", "CAFE", null, "place-2", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("추천 칸");
	}

	@Test
	@DisplayName("영업 종료 · place ID 없음 · place ID 공백인 매장 → 후보에서 뺀다")
	void excludesIneligibleStores() {
		// given: 같은 칸에 적격 1곳과 부적격 3곳
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.store(1, 101, "적격", "KOREAN", null, "place-1", true);
		db.store(2, 101, "폐업", "KOREAN", null, "place-2", false);
		db.store(3, 101, "place ID 없음", "KOREAN", null, null, true);
		db.store(4, 101, "place ID 공백", "KOREAN", null, "   ", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("적격");
	}

	@Test
	@DisplayName("다른 배치 · 요일 · 시간대의 조건이 있다 → 요청한 조건의 칸만 후보로 준다")
	void scopedByCondition() {
		// given: 칸 101 만 (ds, mon, lunch) 조건이고, 나머지는 하나씩 어긋난다
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", true);
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition("ds-other", 102, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, "tue", LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, MON, "dinner", "AVAILABLE", 90.0);
		db.store(1, 101, "맞는 칸", "KOREAN", null, "place-1", true);
		db.store(2, 102, "다른 배치", "KOREAN", null, "place-2", true);
		db.store(3, 103, "다른 요일·시간대", "KOREAN", null, "place-3", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("맞는 칸");
	}

	@Test
	@DisplayName("조건을 하나씩 어긋나게 섞는다 → 모두 맞는 매장 한 곳만 남는다")
	void keepsOnlyTheFullyMatchingStore() {
		// given: 정상 1곳과, 한 가지씩만 어긋난 행들
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", true);
		db.cell(104);
		db.cell(105);
		db.cell(106);
		db.cell(107);
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition("ds-other", 102, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, "tue", LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 104, MON, "dinner", "AVAILABLE", 90.0);
		db.condition(DS, 105, MON, LUNCH, "AVAILABLE", 74.0);
		db.condition(DS, 106, MON, LUNCH, "INCOMPLETE", null);
		db.condition(DS, 107, MON, LUNCH, "AVAILABLE", 90.0);
		db.store(1, 101, "정상", "KOREAN", null, "place-1", true);
		db.store(2, 102, "다른 배치", "KOREAN", null, "place-2", true);
		db.store(3, 103, "다른 요일", "KOREAN", null, "place-3", true);
		db.store(4, 104, "다른 시간대", "KOREAN", null, "place-4", true);
		db.store(5, 105, "하한 미달", "KOREAN", null, "place-5", true);
		db.store(6, 106, "품질 미달", "KOREAN", null, "place-6", true);
		db.store(7, 107, "폐업", "KOREAN", null, "place-7", false);
		db.store(8, 101, "place ID 공백", "KOREAN", null, "  ", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("정상");
	}

	@Test
	@DisplayName("한 칸에 적격 매장이 여러 곳이다 → 모두 후보로 주고 값은 저장값 그대로 옮긴다")
	void mapsStoredValues() {
		// given
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 88.5);
		db.store(1, 101, "공평식당", "KOREAN", "1995-05-20", "place-1", true);
		db.store(2, 101, "신생 카페", "CAFE", null, "place-2", true);

		// when
		List<Candidate> candidates = candidates(MIN_PERCENTILE);

		// then
		assertThat(candidates).hasSize(2);
		Candidate korean = candidates.stream()
			.filter(c -> c.place().id() == 1).findFirst().orElseThrow();
		assertThat(korean.place()).isEqualTo(new Place(1, "공평식당", 101, StoreKind.KOREAN,
			37.57, 126.98, LocalDate.of(1995, 5, 20), "place-1"));
		assertThat(korean.mgmtNo()).isEqualTo("mgmt-1");
		assertThat(korean.percentile()).isEqualTo(88.5);
		Candidate cafe = candidates.stream()
			.filter(c -> c.place().id() == 2).findFirst().orElseThrow();
		assertThat(cafe.place().kind()).isEqualTo(StoreKind.CAFE);
		assertThat(cafe.place().sinceDate()).isNull();
	}
}
