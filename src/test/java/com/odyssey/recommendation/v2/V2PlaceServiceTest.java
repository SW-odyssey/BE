package com.odyssey.recommendation.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.entity.StoreKind;
import com.odyssey.store.repository.StorePlaceRepository;
import com.odyssey.store.repository.StorePlaceRepository.Place;

/** {@link V2PlaceService} 를 확인한다. 리포지토리는 목으로 대신한다. */
class V2PlaceServiceTest {

	private StorePlaceRepository repository;
	private V2PlaceService service;

	@BeforeEach
	void setUp() {
		repository = mock(StorePlaceRepository.class);
		service = new V2PlaceService(repository);
	}

	@Test
	@DisplayName("있는 장소 → 칸 번호는 문자열로, 나머지 필드는 그대로 응답으로 만든다")
	void found() {
		// given
		given(repository.findActiveById(7L)).willReturn(Optional.of(new Place(
			7, "공평식당", 81591938L, StoreKind.KOREAN, 37.57, 126.98,
			LocalDate.of(1995, 5, 20), "place-7")));

		// when
		V2PlaceResponse response = service.place(7);

		// then
		assertThat(response).isEqualTo(new V2PlaceResponse(
			7, "공평식당", "81591938", StoreKind.KOREAN, 37.57, 126.98,
			LocalDate.of(1995, 5, 20), "place-7"));
	}

	@Test
	@DisplayName("개업일과 구글 ID 가 없는 장소 → 두 값은 null 로 둔다")
	void foundWithNullableFields() {
		// given
		given(repository.findActiveById(7L)).willReturn(Optional.of(new Place(
			7, "공평식당", 101L, StoreKind.CAFE, 37.57, 126.98, null, null)));

		// when
		V2PlaceResponse response = service.place(7);

		// then
		assertThat(response.sinceDate()).isNull();
		assertThat(response.googlePlaceId()).isNull();
	}

	@Test
	@DisplayName("없거나 비활성인 장소 → PLACE_NOT_FOUND")
	void notFound() {
		// given
		given(repository.findActiveById(99L)).willReturn(Optional.empty());

		// when
		BusinessException exception = catchThrowableOfType(BusinessException.class,
			() -> service.place(99));

		// then
		assertThat(exception.getErrorType()).isEqualTo(CommonErrorCode.PLACE_NOT_FOUND);
		assertThat(exception.getMessage()).isEqualTo("현재 제공하는 장소가 아닙니다.");
	}
}
