package com.odyssey.store.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StoreKindTest {

	@Test
	@DisplayName("카페 분류 → 카페 대분류에 속한다")
	void cafeBelongsToCafe() {
		// given
		StoreKind kind = StoreKind.CAFE;

		// when
		Category category = kind.category();

		// then
		assertThat(category).isEqualTo(Category.CAFE);
	}

	@ParameterizedTest
	@EnumSource(value = StoreKind.class, names = {"KOREAN", "BAR", "OTHER"})
	@DisplayName("카페가 아닌 분류 → 음식점 대분류에 속한다")
	void othersBelongToFood(StoreKind kind) {
		// given: 카페를 뺀 나머지 분류

		// when
		Category category = kind.category();

		// then
		assertThat(category).isEqualTo(Category.FOOD);
	}
}
