package com.odyssey.store.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.TestcontainersConfiguration;
import com.odyssey.cell.entity.Area;
import com.odyssey.store.StoreNames;
import com.odyssey.store.StoreNames.Language;
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
	@DisplayName("활성 매장 → 번호, 이름, 칸, 분류, 좌표, 개업일, 구글 ID, 지역을 그대로 준다")
	void findActiveByIdFields() {
		// given
		db.store(7, CELL, "공평식당", "KOREAN", "1995-05-20", "place-7", true);

		// when
		Optional<Place> place = repository.findActiveById(7);

		// then
		assertThat(place).contains(new Place(7, "공평식당", CELL, StoreKind.KOREAN, 37.57, 126.98,
			LocalDate.of(1995, 5, 20), "place-7", StoreNames.EMPTY, Area.INSADONG));
	}

	@Test
	@DisplayName("매장이 속한 칸의 법정동이 명동2가 → 지역은 명동이다")
	void findActiveByIdArea() {
		// given
		db.cell(202, "명동2가", "MYEONGDONG");
		db.store(8, 202, "명동칼국수", "KOREAN", null, "place-8", true);

		// when
		Optional<Place> place = repository.findActiveById(8);

		// then
		assertThat(place).get().extracting(Place::area).isEqualTo(Area.MYEONGDONG);
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

	@Test
	@DisplayName("정확히 일치 → 시작 부분 일치 → 나머지 부분 일치 순으로 주고 비활성 매장은 뺀다")
	void ranksExactThenPrefixThenSubstring() {
		// given
		db.store(9, CELL, "점심 공평식당", "KOREAN", null, "place-9", true);
		db.store(8, CELL, "공평식당 종로점", "KOREAN", null, "place-8", true);
		db.store(7, CELL, "공평식당", "KOREAN", null, null, true);
		db.store(5, CELL, "공평식당", "KOREAN", null, "place-5", false);

		// when
		List<Place> places = repository.searchActiveByName("공평 식당", Language.KO, 6);

		// then
		assertThat(places).extracting(Place::id).containsExactly(7L, 8L, 9L);
	}

	@Test
	@DisplayName("같은 순위 → 요청 언어로 일치한 매장을 먼저 주고, 그다음 번호 순이다")
	void ranksRequestedLanguageMatchFirstWithinSameMatch() {
		// given
		db.store(6, CELL, "Gongpyeong", "BAR", null, "place-6", true);
		db.store(7, CELL, "공평식당", "KOREAN", null, null, true);
		insertNames(7, "공평식당", "Gongpyeong", "公平食堂", "公平食堂");

		// when
		List<Place> places = repository.searchActiveByName("gongpyeong", Language.EN, 6);

		// then
		assertThat(places).extracting(Place::id).containsExactly(7L, 6L);
	}

	@Test
	@DisplayName("동명 매장 → 번호별로 모두 주고 limit 에서 자른다")
	void keepsSameNameStoresUpToLimit() {
		// given
		db.store(8, CELL, "공평식당", "KOREAN", null, "place-8", true);
		db.store(7, CELL, "공평식당", "KOREAN", null, null, true);
		db.store(6, CELL, "공평식당", "BAR", null, "place-6", true);

		// when
		List<Place> places = repository.searchActiveByName("공평식당", Language.KO, 2);

		// then
		assertThat(places).extracting(Place::id).containsExactly(6L, 7L);
	}

	@ParameterizedTest
	@ValueSource(strings = {"GONGPYEONG restaurant", "公平餐厅", "コンピョン食堂"})
	@DisplayName("영어·중국어·일본어 상호 → 요청 언어와 상관없이 찾는다")
	void searchesTranslatedNames(String searchTerm) {
		// given
		db.store(7, CELL, "공평식당", "KOREAN", null, null, true);
		insertNames(7, "공평식당", "Gongpyeong Restaurant", "公平餐厅", "コンピョン食堂");

		// when
		List<Place> places = repository.searchActiveByName(searchTerm, Language.KO, 6);

		// then
		assertThat(places).extracting(Place::id).containsExactly(7L);
	}

	@Test
	@DisplayName("상호가 바뀌어 번역이 옛 상호 것이다 → 번역 상호로는 찾지 않는다")
	void ignoresTranslationsOfPreviousName() {
		// given
		db.store(7, CELL, "새식당", "KOREAN", null, null, true);
		insertNames(7, "공평식당", "Gongpyeong Restaurant", "公平餐厅", "コンピョン食堂");

		// when
		List<Place> places = repository.searchActiveByName("Gongpyeong", Language.EN, 6);

		// then
		assertThat(places).isEmpty();
	}

	@Test
	@DisplayName("% 가 든 검색어 → 와일드카드가 아닌 글자로 비교한다")
	void matchesWildcardCharactersLiterally() {
		// given
		db.store(7, CELL, "카페 100%", "CAFE", null, "place-7", true);
		db.store(8, CELL, "카페 100호", "CAFE", null, "place-8", true);

		// when
		List<Place> places = repository.searchActiveByName("100%", Language.KO, 6);

		// then
		assertThat(places).extracting(Place::id).containsExactly(7L);
	}

	private void insertNames(long id, String original, String english, String chinese, String japanese) {
		jdbc.sql("""
			insert into store_name_translation (store_id, source_name, name_en, name_zh, name_ja)
			values (:id, :original, :english, :chinese, :japanese)
			""").param("id", id).param("original", original).param("english", english)
			.param("chinese", chinese).param("japanese", japanese).update();
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
