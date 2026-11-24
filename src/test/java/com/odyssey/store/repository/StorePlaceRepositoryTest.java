package com.odyssey.store.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.store.StoreNames;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository.Place;
import com.odyssey.support.SplitRecommendationFixture;

/** {@link StorePlaceRepository} 의 활성 필터와 필드 매핑을 실제 Postgres 에서 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class StorePlaceRepositoryTest {

	private static final long CELL = 101;

	@Autowired
	private StorePlaceRepository repository;

	@Autowired
	private JdbcClient jdbc;

	private SplitRecommendationFixture db;

	@BeforeEach
	void setUp() {
		db = new SplitRecommendationFixture(jdbc);
		db.cell(CELL);
	}

	@Test
	@DisplayName("활성 매장 → 번호, 이름, 칸, 분류, 좌표, 개업일, 구글 ID 를 그대로 준다")
	void findActiveByIdFields() {
		// given
		db.store(7, CELL, "공평식당", "KOREAN", "1995-05-20", "place-7", true);

		// when
		Optional<Place> place = repository.findActiveById(7);

		// then
		assertThat(place).contains(new Place(7, "공평식당", CELL, StoreKind.KOREAN, 37.57, 126.98,
			LocalDate.of(1995, 5, 20), "place-7"));
	}

	@Test
	@DisplayName("번역 당시의 상호가 현재 상호와 일치할 때만 번역을 읽는다")
	void findsTranslationsForCurrentName() {
		db.store(7, CELL, "공평식당", "KOREAN", null, "place-7", true);
		jdbc.sql("""
			insert into store_name_translation (store_id, source_name, name_en, name_zh, name_ja)
			values (7, '공평식당', 'Gongpyeong Restaurant', '公平食堂', '公平食堂')
			""").update();

		assertThat(repository.findActiveById(7)).get().extracting(Place::names)
			.isEqualTo(new StoreNames("Gongpyeong Restaurant", "公平食堂", "公平食堂"));

		jdbc.sql("update store set name = '새식당' where id = 7").update();

		assertThat(repository.findActiveById(7)).get().extracting(Place::names).isEqualTo(StoreNames.EMPTY);
	}

	@ParameterizedTest
	@EnumSource(StoreKind.class)
	@DisplayName("가게 분류 네 가지 → 모두 StoreKind 로 읽는다")
	void findActiveByIdKinds(StoreKind kind) {
		// given
		db.store(7, CELL, "공평식당", kind.name(), "1995-05-20", "place-7", true);

		// when
		Optional<Place> place = repository.findActiveById(7);

		// then
		assertThat(place).get().extracting(Place::kind).isEqualTo(kind);
	}

	@Test
	@DisplayName("비활성 매장 → 조회되지 않는다")
	void findActiveByIdInactive() {
		// given
		db.store(7, CELL, "폐업식당", "KOREAN", "1995-05-20", "place-7", false);

		// when
		Optional<Place> place = repository.findActiveById(7);

		// then
		assertThat(place).isEmpty();
	}

	@Test
	@DisplayName("없는 번호 → 조회되지 않는다")
	void findActiveByIdMissing() {
		// given
		db.store(7, CELL, "공평식당", "KOREAN", "1995-05-20", "place-7", true);

		// when
		Optional<Place> place = repository.findActiveById(8);

		// then
		assertThat(place).isEmpty();
	}

	@Test
	@DisplayName("개업일과 구글 ID 가 없는 활성 매장 → 두 값만 null 로 조회한다")
	void findActiveByIdNullableFields() {
		// given
		db.store(7, CELL, "공평식당", "KOREAN", null, null, true);

		// when
		Optional<Place> place = repository.findActiveById(7);

		// then
		assertThat(place).get().satisfies(found -> {
			assertThat(found.sinceDate()).isNull();
			assertThat(found.googlePlaceId()).isNull();
			assertThat(found.name()).isEqualTo("공평식당");
		});
	}
}
