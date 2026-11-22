package com.odyssey.cellvisitprofile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.VisitProfile;
import com.odyssey.cellvisitprofile.VisitProfile.VisitBar;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;
import com.odyssey.support.SplitRecommendationFixture;

/** {@link CellVisitProfileRepository} 의 막대 순서와 섞임 방지를 실제 Postgres 에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CellVisitProfileRepositoryTest {

	private static final String DS = "ds-profile";
	private static final String MON = "mon";

	@Autowired
	private CellVisitProfileRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void setUp() {
		db = new SplitRecommendationFixture(jdbc);
		db.cell(101);
		db.cell(102);
		db.dataset(DS, "2026-10-01", "2026-10-31T00:00:00Z", false);
		db.dataset("ds-other", "2026-09-01", "2026-09-30T00:00:00Z", false);
	}

	private Optional<VisitProfile> find(String datasetId, Weekday weekday, long cellId) {
		return repository.find(datasetId, weekday, cellId);
	}

	@Test
	@DisplayName("프로필이 없다 → 비어 있다")
	void missing() {
		// when
		Optional<VisitProfile> profile = find(DS, Weekday.MON, 101);

		// then
		assertThat(profile).isEmpty();
	}

	@Test
	@DisplayName("막대를 역순으로 넣었다 → 점심 → 오후 → 저녁 → 심야 순서로 돌려준다")
	void barsOrderedBySlot() {
		// given: late_night, dinner, afternoon, lunch 순으로 적재
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "late_night", 70);
		db.bar(DS, 101, MON, "dinner", 100);
		db.bar(DS, 101, MON, "afternoon", 75);
		db.bar(DS, 101, MON, "lunch", 70);

		// when
		VisitProfile profile = find(DS, Weekday.MON, 101).orElseThrow();

		// then
		assertThat(profile.status()).isEqualTo(VisitProfileStatus.AVAILABLE);
		assertThat(profile.bars()).containsExactly(
			new VisitBar(TimeSlot.LUNCH, 70), new VisitBar(TimeSlot.AFTERNOON, 75),
			new VisitBar(TimeSlot.DINNER, 100), new VisitBar(TimeSlot.LATE_NIGHT, 70));
		assertThat(profile.peakSlot()).contains(TimeSlot.DINNER);
	}

	@Test
	@DisplayName("막대가 없는 상태의 프로필 → 상태와 빈 막대로 돌려준다")
	void profileWithoutBars() {
		// given
		db.profile(DS, 101, MON, "NO_DISTINCT_PEAK");

		// when
		VisitProfile profile = find(DS, Weekday.MON, 101).orElseThrow();

		// then
		assertThat(profile.status()).isEqualTo(VisitProfileStatus.NO_DISTINCT_PEAK);
		assertThat(profile.bars()).isEmpty();
	}

	@Test
	@DisplayName("네 가지 상태를 모두 적재한다 → 상태마다 그대로 읽는다")
	void everyStatusIsRead() {
		// given
		db.profile(DS, 101, "mon", "INCOMPLETE_TIME_SLOTS");
		db.profile(DS, 101, "tue", "NO_POSITIVE_VISITS");
		db.profile(DS, 101, "wed", "NO_DISTINCT_PEAK");

		// when & then
		assertThat(find(DS, Weekday.MON, 101).orElseThrow().status())
			.isEqualTo(VisitProfileStatus.INCOMPLETE_TIME_SLOTS);
		assertThat(find(DS, Weekday.TUE, 101).orElseThrow().status())
			.isEqualTo(VisitProfileStatus.NO_POSITIVE_VISITS);
		assertThat(find(DS, Weekday.WED, 101).orElseThrow().status())
			.isEqualTo(VisitProfileStatus.NO_DISTINCT_PEAK);
	}

	@Test
	@DisplayName("같은 칸의 다른 요일 프로필이 있다 → 요청한 요일의 막대만 읽는다")
	void scopedByWeekday() {
		// given
		db.profile(DS, 101, MON, "AVAILABLE");
		db.bar(DS, 101, MON, "lunch", 100);
		db.bar(DS, 101, MON, "afternoon", 10);
		db.bar(DS, 101, MON, "dinner", 20);
		db.bar(DS, 101, MON, "late_night", 30);
		db.profile(DS, 101, "tue", "AVAILABLE");
		db.bar(DS, 101, "tue", "lunch", 1);
		db.bar(DS, 101, "tue", "afternoon", 2);
		db.bar(DS, 101, "tue", "dinner", 3);
		db.bar(DS, 101, "tue", "late_night", 100);

		// when
		VisitProfile tue = find(DS, Weekday.TUE, 101).orElseThrow();

		// then
		assertThat(tue.bars()).extracting(VisitBar::relativeHeight).containsExactly(1, 2, 3, 100);
		assertThat(tue.peakSlot()).contains(TimeSlot.LATE_NIGHT);
	}

	@Test
	@DisplayName("다른 칸의 프로필만 있다 → 비어 있고 다른 칸의 막대가 섞이지 않는다")
	void scopedByCell() {
		// given
		db.profile(DS, 102, MON, "AVAILABLE");
		db.bar(DS, 102, MON, "lunch", 100);
		db.bar(DS, 102, MON, "afternoon", 10);
		db.bar(DS, 102, MON, "dinner", 20);
		db.bar(DS, 102, MON, "late_night", 30);
		db.profile(DS, 101, MON, "NO_POSITIVE_VISITS");

		// when
		VisitProfile profile = find(DS, Weekday.MON, 101).orElseThrow();

		// then
		assertThat(profile.status()).isEqualTo(VisitProfileStatus.NO_POSITIVE_VISITS);
		assertThat(profile.bars()).isEmpty();
		assertThat(find(DS, Weekday.MON, 999)).isEmpty();
	}

	@Test
	@DisplayName("다른 분석 배치에만 프로필이 있다 → 비어 있다")
	void scopedByDataset() {
		// given
		db.profile("ds-other", 101, MON, "NO_DISTINCT_PEAK");

		// when
		Optional<VisitProfile> profile = find(DS, Weekday.MON, 101);

		// then
		assertThat(profile).isEmpty();
		assertThat(find("ds-other", Weekday.MON, 101)).isPresent();
	}

	private void available(long cellId, String weekday, int lunch, int afternoon, int dinner, int lateNight) {
		db.profile(DS, cellId, weekday, "AVAILABLE");
		db.bar(DS, cellId, weekday, "lunch", lunch);
		db.bar(DS, cellId, weekday, "afternoon", afternoon);
		db.bar(DS, cellId, weekday, "dinner", dinner);
		db.bar(DS, cellId, weekday, "late_night", lateNight);
	}

	@Test
	@DisplayName("그래프가 있는 칸 둘과 없는 칸 하나 → 그래프가 있는 칸의 가장 붐비는 시간대만 돌려준다")
	void peakSlots() {
		// given
		available(101, MON, 100, 10, 20, 30);
		available(102, MON, 10, 20, 30, 100);
		db.cell(103);
		db.profile(DS, 103, MON, "NO_DISTINCT_PEAK");

		// when
		Map<Long, TimeSlot> peaks = repository.findPeakSlots(DS, Weekday.MON, List.of(101L, 102L, 103L, 999L));

		// then
		assertThat(peaks).containsExactlyInAnyOrderEntriesOf(
			Map.of(101L, TimeSlot.LUNCH, 102L, TimeSlot.LATE_NIGHT));
	}

	@Test
	@DisplayName("같은 칸의 다른 요일·다른 배치 그래프 → 요청한 배치·요일의 것만 본다")
	void peakSlotsScoped() {
		// given
		available(101, "tue", 100, 10, 20, 30);
		db.profile("ds-other", 101, MON, "AVAILABLE");
		db.bar("ds-other", 101, MON, "lunch", 100);
		db.bar("ds-other", 101, MON, "afternoon", 10);
		db.bar("ds-other", 101, MON, "dinner", 20);
		db.bar("ds-other", 101, MON, "late_night", 30);
		available(101, MON, 10, 20, 100, 30);

		// when
		Map<Long, TimeSlot> peaks = repository.findPeakSlots(DS, Weekday.MON, List.of(101L));

		// then
		assertThat(peaks).containsExactlyEntriesOf(Map.of(101L, TimeSlot.DINNER));
	}

	@Test
	@DisplayName("칸 목록이 비었다 → 빈 결과다")
	void peakSlotsEmpty() {
		// when
		Map<Long, TimeSlot> peaks = repository.findPeakSlots(DS, Weekday.MON, List.of());

		// then
		assertThat(peaks).isEmpty();
	}
}
