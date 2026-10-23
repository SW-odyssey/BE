package com.odyssey.store.entity;

import com.odyssey.cell.entity.Cell;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

import static jakarta.persistence.FetchType.*;

/**
 * 영업 중인 음식점 · 카페 (약 2,200곳).
 *
 * <p>원본은 행정안전부 지방행정 인허가 데이터(공공데이터)이고,
 * 분류(category · foodType)는 analysis 가 상가정보 업종으로 정한다.
 * analysis 가 mgmtNo 로 upsert 하고 행은 지우지 않는다.
 */
@Entity
@Immutable
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 인허가 관리번호 (예: 3010000-101-1981-07027)
	// 같은 가게가 여러 번 등록된 경우 analysis 가 하나로 합치고 대표 번호만 남긴다
	@Column(nullable = false, unique = true, length = 40)
	private String mgmtNo;

	// 매장이 있는 50m 칸
	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "cell_id")
	private Cell cell;

	// 인허가 등록된 상호 (예: 개화)
	@Column(nullable = false, length = 100)
	private String name;

	// 상호 로마자 표기 (외국인용). 만드는 방법이 정해지지 않아 지금은 비어있음
	@Column(length = 200)
	private String nameRomanized;

	// FOOD(음식점) / CAFE(카페)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Category category;

	// 음식점 하위 종류 KOREAN / BAR / OTHER. 카페면 비어있음
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private FoodType foodType;

	// 인허가 업태 (한식 / 중국식 / 커피숍 / 호프/통닭), 기타도 존재
	// category 보다 세부 분류이고 필터나 태그에 사용 가능
	@Column(length = 50)
	private String subType;

	// 프랜차이즈 여부. 대형 체인 목록과 일치하거나,
	// 같은 상호가 '00점' 형태로 종로구 · 중구에 2곳 이상이면 true
	private boolean franchise;

	// 도로명 주소. 인허가에 도로명이 없으면 지번 주소로 채움
	@Column(nullable = false, length = 200)
	private String roadAddr;

	// 영문 주소. 아직 비어 있다
	@Column(length = 300)
	private String roadAddrEn;

	// 위도 경도 (WGS84), 인허가 좌표 (EPSG:5174)를 analysis 가 변환한 값
	private double lat;
	private double lng;

	// 인허가 일자, 개업일로 오래된 가게(노포) 구분 시 사용 가능
	@Column(nullable = false)
	private LocalDate licenseDate;

	// 구글 장소 ID, 영업시간, 평점 같은 상세를 실시간 조회시 키로 사용
	@Column(length = 100)
	private String googlePlaceId;

	// 최근 적재 목록에 있으면 true, 목록에서 빠질 경우 폐업으로 보고 false 로 바꿈
	// API 는 active 인 매장만 보여준다
	private boolean active;

}
