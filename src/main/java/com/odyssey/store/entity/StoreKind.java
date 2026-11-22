package com.odyssey.store.entity;

/** analysis 가 정한 단일 가게 분류. API 의 Category/FoodType 과 별개다. */
public enum StoreKind {
	CAFE(Category.CAFE),
	KOREAN(Category.FOOD),
	BAR(Category.FOOD),
	OTHER(Category.FOOD),
	/** 상가정보 소매 업종에서 고른 구경할 만한 가게. 인허가 관리번호 대신 SG-상가업소번호를 쓴다. */
	SHOP(Category.SHOP),
	PLAY(Category.PLAY);

	private final Category category;

	StoreKind(Category category) {
		this.category = category;
	}

	/** 이 분류가 속한 대분류. category 와 kind 를 함께 거를 때 조합 규칙으로 쓴다. */
	public Category category() {
		return category;
	}
}
