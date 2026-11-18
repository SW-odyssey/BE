package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.Place;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/** {@link V2PlaceService} 를 확인한다. 리포지토리는 목으로 대신한다. */
class V2PlaceServiceTest {

	private CellInsightRepository repo;
	private V2PlaceService service;

	@BeforeEach
	void setup() {
		repo = mock(CellInsightRepository.class);
		service = new V2PlaceService(repo);
	}

	@Test
	@DisplayName("있는 장소 → 리포지토리가 준 장소를 그대로 돌려준다")
	void found() {
		// given
		Place place = new Place(7, "공평식당", "10", "korean", 37.57, 126.98,
			LocalDate.of(1995, 5, 20), "place-7");
		when(repo.place(7L)).thenReturn(Optional.of(place));

		// when
		Place result = service.place(7);

		// then
		assertThat(result).isEqualTo(place);
	}

	@Test
	@DisplayName("없거나 폐업한 장소 → PLACE_NOT_FOUND")
	void notFound() {
		// given
		when(repo.place(99L)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.place(99)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.PLACE_NOT_FOUND));
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("장소 번호가 0 이하 → INVALID_INPUT, DB 는 조회하지 않는다")
	void invalidId(long id) {
		// when / then
		assertThatThrownBy(() -> service.place(id)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getErrorType()).isEqualTo(CommonErrorCode.INVALID_INPUT));
		verify(repo, never()).place(anyLong());
	}
}
