package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.repository.AreaCenter;
import com.odyssey.cell.repository.CellRepository;
import com.odyssey.cell.TimeSlot;
import com.odyssey.recommendation.v2.V2MetaResponse.AreaOption;
import com.odyssey.recommendation.v2.V2MetaResponse.DatasetOption;
import com.odyssey.recommendation.v2.V2MetaResponse.ServiceHours;
import com.odyssey.recommendation.v2.V2MetaResponse.SlotWindow;
import com.odyssey.recommendationdataset.repository.RecommendationDatasetRepository.Dataset;

/**
 * {@link V2MetaService} 를 확인한다. 선택지의 순서는 프론트 목(meta.json)과의 약속이라 목록 전체를 고정한다.
 */
class V2MetaServiceTest {

	private V2DatasetResolver datasets;
	private CellRepository cells;
	private V2MetaService service;

	@BeforeEach
	void setUp() {
		datasets = mock(V2DatasetResolver.class);
		cells = mock(CellRepository.class);
		service = new V2MetaService(datasets, cells);
		given(datasets.findSupported()).willReturn(List.of());
		given(cells.findAreaCenters()).willReturn(List.of());
	}

	@Test
	@DisplayName("지원하는 배치가 있다 → id, 기준월, 대상을 그대로 받은 순서대로 준다")
	void datasetOptions() {
		// given
		given(datasets.findSupported()).willReturn(List.of(
			new Dataset("2025-12-a", "2025-12", "DOMESTIC_20_39", "p", "r", "v"),
			new Dataset("2025-11-b", "2025-11", "DOMESTIC_20_39", "p", "r", "v")));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.datasets()).containsExactly(
			new DatasetOption("2025-12-a", "2025-12", "DOMESTIC_20_39"),
			new DatasetOption("2025-11-b", "2025-11", "DOMESTIC_20_39"));
	}

	@Test
	@DisplayName("지원하는 배치가 둘이다 → 기본 배치는 가장 최근 기준월의 id 다")
	void defaultDatasetIsNewest() {
		// given
		given(datasets.findSupported()).willReturn(List.of(
			new Dataset("2025-12-a", "2025-12", "DOMESTIC_20_39", "p", "r", "v"),
			new Dataset("2025-11-b", "2025-11", "DOMESTIC_20_39", "p", "r", "v")));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.defaultDatasetId()).isEqualTo("2025-12-a");
	}

	@Test
	@DisplayName("지원하는 배치가 없다 → 배치는 빈 목록이고 기본 배치는 null 이며 고정 선택지는 그대로 준다")
	void emptyDatasetsKeepFixedChoices() {
		// given: 적재가 끝나기 전의 빈 DB

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.datasets()).isEmpty();
		assertThat(meta.defaultDatasetId()).isNull();
		assertThat(meta.weekdays()).isNotEmpty();
	}

	@Test
	@DisplayName("칸이 있는 지역 → 받은 순서와 달리 종로, 익선동, 인사동, 을지로, 명동 순서로 준다")
	void areasFollowDisplayOrder() {
		// given
		given(cells.findAreaCenters()).willReturn(List.of(
			new AreaCenter(Area.MYEONGDONG, 37.5636, 126.9826),
			new AreaCenter(Area.JONGNO, 37.5704, 126.9922)));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.areas()).containsExactly(
			new AreaOption(Area.JONGNO, "종로", 37.5704, 126.9922),
			new AreaOption(Area.MYEONGDONG, "명동", 37.5636, 126.9826));
	}

	@Test
	@DisplayName("지역 중심 좌표 → 소수 여섯째 자리까지 반올림한다")
	void areaCenterIsRoundedToSixDecimals() {
		// given
		given(cells.findAreaCenters()).willReturn(List.of(
			new AreaCenter(Area.EULJIRO, 37.56634567891, 126.99197654321)));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.areas()).containsExactly(
			new AreaOption(Area.EULJIRO, "을지로", 37.566346, 126.991977));
	}

	@Test
	@DisplayName("칸이 없는 지역 → 지역 목록에 넣지 않는다")
	void areaWithoutCellsIsLeftOut() {
		// given: 익선동 칸만 적재된 상태
		given(cells.findAreaCenters()).willReturn(List.of(
			new AreaCenter(Area.IKSEON, 37.5746, 126.9893)));

		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.areas()).extracting(AreaOption::id).containsExactly(Area.IKSEON);
	}

	@Test
	@DisplayName("시간대 경계 → 점심 11~13, 오후 14~17, 저녁 18~21, 심야 22~01 시이고 심야만 다음 날에 끝난다")
	void slotWindows() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.slotWindows()).containsExactly(
			new SlotWindow(TimeSlot.LUNCH, "11:00", "13:00", false),
			new SlotWindow(TimeSlot.AFTERNOON, "14:00", "17:00", false),
			new SlotWindow(TimeSlot.DINNER, "18:00", "21:00", false),
			new SlotWindow(TimeSlot.LATE_NIGHT, "22:00", "01:00", true));
	}

	@Test
	@DisplayName("서비스 시간 → 11:00 에 시작해 다음 날 01:00 에 끝난다")
	void serviceHours() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.serviceHours()).isEqualTo(new ServiceHours("11:00", "01:00", true));
	}

	@Test
	@DisplayName("요일 선택지 → 월요일부터 일요일 순서다")
	void weekdays() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.weekdays())
			.containsExactly("mon", "tue", "wed", "thu", "fri", "sat", "sun");
	}

	@Test
	@DisplayName("시간대 선택지 → 점심, 오후, 저녁, 심야 순서다")
	void slots() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.slots()).containsExactly("lunch", "afternoon", "dinner", "late_night");
	}

	@Test
	@DisplayName("대분류 선택지 → 음식점, 카페, 쇼핑, 놀거리, 전체 순서다")
	void categories() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.categories()).containsExactly("food", "cafe", "shop", "play", "all");
	}

	@Test
	@DisplayName("가게 분류 선택지 → enum 선언 순서가 아니라 화면 순서(한식, 술집, 기타, 카페, 쇼핑, 놀거리, 전체)다")
	void kinds() {
		// when
		V2MetaResponse meta = service.meta();

		// then
		assertThat(meta.kinds()).containsExactly("korean", "bar", "other", "cafe", "shop", "play", "all");
	}
}
