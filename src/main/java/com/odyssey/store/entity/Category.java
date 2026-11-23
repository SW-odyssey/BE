package com.odyssey.store.entity;

/**
 * 매장 대분류. analysis 가 상가정보 업종으로 정하고, 상가정보에 짝이 없으면 인허가 업태로 정한다
 */
public enum Category {
	FOOD,   // 음식점
	CAFE,   // 카페 (비알코올 음료 · 빵/도넛 · 떡/한과 · 아이스크림/빙수)
	SHOP,   // 구경할 만한 소매점 (공예·갤러리·한복 등). v1 화면에는 나오지 않는다
	PLAY    // 체험·놀이 장소
}
