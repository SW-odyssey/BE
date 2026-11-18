package com.odyssey.store.entity;

/** analysis 가 정한 단일 가게 분류. API 의 Category/FoodType 과 별개다. */
public enum StoreKind {
	CAFE(Category.CAFE),
	KOREAN(Category.FOOD),
	BAR(Category.FOOD),
	OTHER(Category.FOOD);

	private final Category category;

	StoreKind(Category category) {
		this.category = category;
	}

	/** 이 분류가 속한 대분류. category 와 kind 를 함께 거를 때 조합 규칙으로 쓴다. */
	public Category category() {
		return category;
	}
}
