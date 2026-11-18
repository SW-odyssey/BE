package com.odyssey.cellinsight.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

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
import com.odyssey.cellinsight.repository.CellInsightRepository.Candidate;
import com.odyssey.cellinsight.repository.CellInsightRepository.Dataset;
import com.odyssey.cellinsight.repository.CellInsightRepository.Place;
import com.odyssey.cellinsight.repository.CellInsightRepository.ProfileRow;
import com.odyssey.cellinsight.repository.CellInsightRepository.ReasonRow;
import com.odyssey.cellinsight.repository.CellInsightRepository.VisitBar;

/**
 * {@link CellInsightRepository} 의 SQL 을 실제 Postgres(Testcontainers)에서 확인한다.
 *
 * <p>SQL 문자열이라 조건이나 정렬이 바뀌어도 컴파일은 통과한다. 후보 선정 조건, 이유 조인, 막대 정렬처럼
 * 응답에 바로 드러나는 규칙을 DB 를 거쳐 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellInsightRepositoryTest {

	private static final String DS = "ds-2026-10";
	private static final String MON = "mon";
	private static final String LUNCH = "lunch";

	@Autowired
	private CellInsightRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void seedCells() {
		db = new SplitRecommendationFixture(jdbc);
		db.cell(101);
		db.cell(102);
		db.cell(103);
	}

	private void baseDataset() {
		db.dataset(DS, "2026-10-01", "2026-10-31T00:00:00Z", true);
	}

	/** 프로필 시드용. 공개 전에 넣고 publish 로 공개하면 DB 트리거가 시드 자체를 검증해 준다. */
	private void draftDataset() {
		db.dataset(DS, "2026-10-01", "2026-10-31T00:00:00Z", false);
	}

	// ---------------------------------------------------------------- datasets

	@Test
	@DisplayName("공개된 데이터셋이 없다 → 빈 목록을 준다")
	void datasetsEmpty() {
		// given: 공개 전 데이터셋만 있다
		db.dataset("ds-draft", "2026-10-01", "2026-10-31T00:00:00Z", false);

		// when
		List<Dataset> datasets = repository.datasets();

		// then
		assertThat(datasets).isEmpty();
	}

	@Test
	@DisplayName("공개 전 데이터셋이 섞여 있다 → 공개된 것만 준다")
	void datasetsOnlyPublished() {
		// given
		db.dataset("ds-public", "2026-10-01", "2026-10-31T00:00:00Z", true);
		db.dataset("ds-draft", "2026-11-01", "2026-11-02T00:00:00Z", false);

		// when
		List<Dataset> datasets = repository.datasets();

		// then
		assertThat(datasets).extracting(Dataset::id).containsExactly("ds-public");
	}

	@Test
	@DisplayName("기준월과 계산 시각이 제각각이다 → 최신 기준월, 같은 달이면 최근 계산, 그래도 같으면 ID 순으로 준다")
	void datasetsOrder() {
		// given: 일부러 정렬 순서와 다르게 넣는다
		db.dataset("ds-sep", "2026-09-01", "2026-12-01T00:00:00Z", true);
		db.dataset("ds-oct-early", "2026-10-01", "2026-10-31T08:00:00Z", true);
		db.dataset("ds-b-oct-late", "2026-10-01", "2026-11-01T00:00:00Z", true);
		db.dataset("ds-a-oct-late", "2026-10-01", "2026-11-01T00:00:00Z", true);

		// when
		List<Dataset> datasets = repository.datasets();

		// then
		assertThat(datasets).extracting(Dataset::id)
			.containsExactly("ds-a-oct-late", "ds-b-oct-late", "ds-oct-early", "ds-sep");
	}

	@Test
	@DisplayName("데이터셋 한 건 → 기준월은 YYYY-MM, 대상은 고정값, 버전 셋은 저장값 그대로 준다")
	void datasetsFields() {
		// given
		baseDataset();

		// when
		List<Dataset> datasets = repository.datasets();

		// then
		assertThat(datasets).containsExactly(
			new Dataset(DS, "2026-10", "DOMESTIC_20_39", "policy-1", "reason-1", "profile-1"));
	}

	// -------------------------------------------------------------- candidates

	@Test
	@DisplayName("방문 백분위가 75 이상이다 → 후보에 넣고, 75 미만은 뺀다")
	void candidatesPercentileBoundary() {
		// given: 경계값 75.0 은 포함, 74.99 는 제외
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 75.0);
		db.condition(DS, 102, MON, LUNCH, "AVAILABLE", 74.99);
		db.store(1, 101, "경계 포함", "KOREAN", "1995-05-20", "place-1", true);
		db.store(2, 102, "경계 미만", "KOREAN", "1995-05-20", "place-2", true);

		// when
		List<Candidate> candidates = repository.candidates(DS, MON, LUNCH);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("경계 포함");
	}

	@Test
	@DisplayName("품질 상태가 AVAILABLE 이 아니다 → 후보에서 뺀다")
	void candidatesExcludeNotAvailable() {
		// given: 관측되지 않은 칸은 백분위가 없다 (CHECK 제약)
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 102, MON, LUNCH, "NOT_OBSERVED", null);
		db.store(1, 101, "추천 칸", "CAFE", null, "place-1", true);
		db.store(2, 102, "관측 안 된 칸", "CAFE", null, "place-2", true);

		// when
		List<Candidate> candidates = repository.candidates(DS, MON, LUNCH);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("추천 칸");
	}

	@Test
	@DisplayName("영업 종료 · place ID 없음 · place ID 공백인 매장 → 후보에서 뺀다")
	void candidatesExcludeIneligibleStores() {
		// given: 같은 칸에 적격 1곳과 부적격 3곳
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.store(1, 101, "적격", "KOREAN", null, "place-1", true);
		db.store(2, 101, "폐업", "KOREAN", null, "place-2", false);
		db.store(3, 101, "place ID 없음", "KOREAN", null, null, true);
		db.store(4, 101, "place ID 공백", "KOREAN", null, "   ", true);

		// when
		List<Candidate> candidates = repository.candidates(DS, MON, LUNCH);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("적격");
	}

	@Test
	@DisplayName("다른 데이터셋 · 요일 · 시간대의 조건이 있다 → 요청한 조건의 칸만 후보로 준다")
	void candidatesScopedByCondition() {
		// given: 칸 101 만 (ds, mon, lunch) 조건이고, 나머지는 하나씩 어긋난다
		baseDataset();
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", true);
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition("ds-other", 102, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, "tue", LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, MON, "dinner", "AVAILABLE", 90.0);
		db.store(1, 101, "맞는 칸", "KOREAN", null, "place-1", true);
		db.store(2, 102, "다른 데이터셋", "KOREAN", null, "place-2", true);
		db.store(3, 103, "다른 요일·시간대", "KOREAN", null, "place-3", true);

		// when
		List<Candidate> candidates = repository.candidates(DS, MON, LUNCH);

		// then
		assertThat(candidates).extracting(c -> c.place().name()).containsExactly("맞는 칸");
	}

	@Test
	@DisplayName("한 칸에 적격 매장이 여러 곳이다 → 모두 후보로 주고 값은 저장값 그대로 옮긴다")
	void candidatesMapping() {
		// given
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 88.5);
		db.store(1, 101, "공평식당", "KOREAN", "1995-05-20", "place-1", true);
		db.store(2, 101, "신생 카페", "CAFE", null, "place-2", true);

		// when
		List<Candidate> candidates = repository.candidates(DS, MON, LUNCH);

		// then
		assertThat(candidates).hasSize(2);
		Candidate korean = candidates.stream()
			.filter(c -> c.place().id() == 1).findFirst().orElseThrow();
		assertThat(korean.place()).isEqualTo(new Place(1, "공평식당", "101", "korean",
			37.57, 126.98, LocalDate.of(1995, 5, 20), "place-1"));
		assertThat(korean.mgmtNo()).isEqualTo("mgmt-1");
		assertThat(korean.percentile()).isEqualTo(88.5);
		Candidate cafe = candidates.stream()
			.filter(c -> c.place().id() == 2).findFirst().orElseThrow();
		assertThat(cafe.place().kind()).isEqualTo("cafe");
		assertThat(cafe.place().sinceDate()).isNull();
	}

	// ----------------------------------------------------------------- reasons

	@Test
	@DisplayName("이유가 둘인 칸 → 넣은 순서와 상관없이 위치 순서대로 준다")
	void reasonsOrderedByPosition() {
		// given: 위치 2 를 먼저 넣는다
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.reason(DS, 101, MON, LUNCH, 2, "TIME_PEAK");
		db.reason(DS, 101, MON, LUNCH, 1, "VISIT_RELATIVE_HIGH");

		// when
		List<ReasonRow> rows = repository.reasons(DS, MON, LUNCH, List.of(101L));

		// then
		assertThat(rows).containsExactly(
			new ReasonRow("101", "AVAILABLE", 1, "VISIT_RELATIVE_HIGH"),
			new ReasonRow("101", "AVAILABLE", 2, "TIME_PEAK"));
	}

	@Test
	@DisplayName("이유가 없는 칸 → 칸과 품질 상태는 남기고 위치와 코드는 null 로 준다")
	void reasonsKeepCellWithoutReasons() {
		// given
		baseDataset();
		db.condition(DS, 102, MON, LUNCH, "NOT_OBSERVED", null);

		// when
		List<ReasonRow> rows = repository.reasons(DS, MON, LUNCH, List.of(102L));

		// then
		assertThat(rows).containsExactly(new ReasonRow("102", "NOT_OBSERVED", null, null));
	}

	@Test
	@DisplayName("여러 칸을 한 번에 요청한다 → 칸 번호순으로 주고 요청하지 않은 칸 · 없는 칸은 뺀다")
	void reasonsMultipleCells() {
		// given: 칸 102 는 조건이 있어도 요청하지 않는다. 999 는 조건 자체가 없다
		baseDataset();
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 102, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 103, MON, LUNCH, "AVAILABLE", 90.0);
		db.reason(DS, 101, MON, LUNCH, 1, "DAY_DISTINCT");
		db.reason(DS, 102, MON, LUNCH, 1, "STAY_DISTINCT");
		db.reason(DS, 103, MON, LUNCH, 1, "WALK_DISTINCT");

		// when: 요청 순서를 거꾸로 준다
		List<ReasonRow> rows = repository.reasons(DS, MON, LUNCH, List.of(103L, 101L, 999L));

		// then
		assertThat(rows).extracting(ReasonRow::cellId).containsExactly("101", "103");
		assertThat(rows).extracting(ReasonRow::code).containsExactly("DAY_DISTINCT", "WALK_DISTINCT");
	}

	@Test
	@DisplayName("같은 칸에 다른 요일 · 시간대 · 데이터셋의 이유가 있다 → 요청한 조건의 이유만 준다")
	void reasonsScopedByCondition() {
		// given
		baseDataset();
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", true);
		db.condition(DS, 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 101, "tue", LUNCH, "AVAILABLE", 90.0);
		db.condition(DS, 101, MON, "dinner", "AVAILABLE", 90.0);
		db.condition("ds-other", 101, MON, LUNCH, "AVAILABLE", 90.0);
		db.reason(DS, 101, MON, LUNCH, 1, "VISIT_RELATIVE_HIGH");
		db.reason(DS, 101, "tue", LUNCH, 1, "DAY_DISTINCT");
		db.reason(DS, 101, MON, "dinner", 1, "STAY_DISTINCT");
		db.reason("ds-other", 101, MON, LUNCH, 1, "WALK_DISTINCT");

		// when
		List<ReasonRow> rows = repository.reasons(DS, MON, LUNCH, List.of(101L));

		// then
		assertThat(rows).extracting(ReasonRow::code).containsExactly("VISIT_RELATIVE_HIGH");
	}

	@Test
	@DisplayName("칸 30개를 한 번에 요청한다 → 30개 모두 칸 번호순으로 준다")
	void reasonsThirtyCells() {
		// given
		baseDataset();
		List<Long> ids = LongStream.rangeClosed(201, 230).boxed().toList();
		for (long id : ids) {
			db.cell(id);
			db.condition(DS, id, MON, LUNCH, "AVAILABLE", 80.0);
		}

		// when
		List<ReasonRow> rows = repository.reasons(DS, MON, LUNCH, ids);

		// then
		assertThat(rows).extracting(ReasonRow::cellId)
			.containsExactlyElementsOf(ids.stream().map(String::valueOf).toList());
	}

	// ----------------------------------------------------------------- profile

	@Test
	@DisplayName("프로필이 없는 칸 → 빈 값을 준다")
	void profileMissing() {
		// given
		baseDataset();

		// when
		var profile = repository.profile(DS, MON, 101L);

		// then
		assertThat(profile).isEmpty();
	}

	@Test
	@DisplayName("AVAILABLE 프로필 → 상태와 막대 4개를 점심 · 오후 · 저녁 · 심야 순서로 준다")
	void profileBarsOrdered() {
		// given: 막대를 일부러 뒤섞어 넣고, 가장 높은 막대(100)는 하나만 둔다
		draftDataset();
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "late_night", 10);
		db.bar(DS, 101, MON, "dinner", 100);
		db.bar(DS, 101, MON, "lunch", 40);
		db.bar(DS, 101, MON, "afternoon", 60);
		db.publish(DS);

		// when
		ProfileRow profile = repository.profile(DS, MON, 101L).orElseThrow();

		// then
		assertThat(profile.status()).isEqualTo("AVAILABLE");
		assertThat(profile.bars()).containsExactly(
			new VisitBar("lunch", 40),
			new VisitBar("afternoon", 60),
			new VisitBar("dinner", 100),
			new VisitBar("late_night", 10));
	}

	@Test
	@DisplayName("AVAILABLE 이 아닌 프로필 → 상태만 주고 막대는 빈 목록이다")
	void profileWithoutBars() {
		// given
		draftDataset();
		db.profile(DS, 101, MON, "NO_POSITIVE_VISITS");
		db.publish(DS);

		// when
		ProfileRow profile = repository.profile(DS, MON, 101L).orElseThrow();

		// then
		assertThat(profile.status()).isEqualTo("NO_POSITIVE_VISITS");
		assertThat(profile.bars()).isEmpty();
	}

	private void givenProfilesInOtherScopes() {
		// 칸 101 은 월요일만 AVAILABLE 이고, 화요일 · 다른 칸 · 다른 데이터셋에는 다른 프로필을 둔다
		draftDataset();
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", true);
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 100);
		db.bar(DS, 101, MON, "afternoon", 20);
		db.bar(DS, 101, MON, "dinner", 20);
		db.bar(DS, 101, MON, "late_night", 20);
		db.profile(DS, 101, "tue", "NO_DISTINCT_PEAK");
		db.profile("ds-other", 102, MON, "INCOMPLETE_TIME_SLOTS");
		db.publish(DS);
	}

	@Test
	@DisplayName("같은 칸에 요일이 다른 프로필이 있다 → 요청한 요일의 프로필만 준다")
	void profileScopedByWeekday() {
		// given
		givenProfilesInOtherScopes();

		// when
		ProfileRow tue = repository.profile(DS, "tue", 101L).orElseThrow();

		// then
		assertThat(tue.status()).isEqualTo("NO_DISTINCT_PEAK");
		assertThat(tue.bars()).isEmpty();
	}

	@Test
	@DisplayName("다른 칸의 프로필만 있다 → 요청한 칸은 빈 값을 준다")
	void profileScopedByCell() {
		// given
		givenProfilesInOtherScopes();

		// when
		var profile = repository.profile(DS, MON, 102L);

		// then
		assertThat(profile).isEmpty();
	}

	@Test
	@DisplayName("다른 데이터셋의 프로필만 있다 → 요청한 데이터셋은 빈 값을 준다")
	void profileScopedByDataset() {
		// given
		givenProfilesInOtherScopes();

		// when
		var profile = repository.profile("ds-other", MON, 101L);

		// then
		assertThat(profile).isEmpty();
	}

	// ------------------------------------------------------------------- place

	@Test
	@DisplayName("영업 중인 매장 → 저장된 값을 그대로 준다")
	void placeFound() {
		// given
		db.store(7, 101, "공평식당", "BAR", "1968-03-11", "place-7", true);

		// when
		var place = repository.place(7L);

		// then
		assertThat(place).contains(new Place(7, "공평식당", "101", "bar",
			37.57, 126.98, LocalDate.of(1968, 3, 11), "place-7"));
	}

	@Test
	@DisplayName("영업 종료한 매장 → 빈 값을 준다")
	void placeClosed() {
		// given
		db.store(8, 101, "닫은 가게", "KOREAN", null, "place-8", false);

		// when
		var place = repository.place(8L);

		// then
		assertThat(place).isEmpty();
	}

	@Test
	@DisplayName("없는 ID → 빈 값을 준다")
	void placeUnknownId() {
		// when
		var place = repository.place(999L);

		// then
		assertThat(place).isEmpty();
	}

	@Test
	@DisplayName("place ID 가 없는 영업 매장 → 조회는 되고 googlePlaceId 는 null 이다")
	void placeWithoutGooglePlaceId() {
		// given: 후보 선정과 달리 단건 조회는 place ID 를 요구하지 않는다
		db.store(9, 101, "place ID 없는 가게", "CAFE", null, null, true);

		// when
		var place = repository.place(9L);

		// then
		assertThat(place).isPresent();
		assertThat(place.get().googlePlaceId()).isNull();
	}
}
