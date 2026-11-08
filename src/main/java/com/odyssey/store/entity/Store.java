package com.odyssey.store.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;
import java.time.LocalDate;

/**
 * 음식점 · 카페. 목록에서 빠진 매장도 비활성 상태로 보관한다.
 *
 * <p>원본은 행정안전부 지방행정 인허가 데이터(공공데이터)이고,
 * 분류(kind)는 analysis 가 상가정보 업종으로 정한다.
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
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	// 인허가 등록된 상호 (예: 개화)
	@Column(nullable = false, length = 100)
	private String name;

	// CAFE / KOREAN / BAR / OTHER. API의 category · foodType은 이 값에서 만든다.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StoreKind kind;

	// 위도 경도 (WGS84), 인허가 좌표 (EPSG:5174)를 analysis 가 변환한 값
	private double lat;
	private double lng;

	// analysis 가 인허가 이력을 연결해 계산한다. licenseDate 로 대체하지 않는다.
	// 비활성 구 데이터는 이력 기준 개업일이 없을 수 있다.
	private LocalDate sinceDate;

	// 구글 장소 ID, 영업시간, 평점 같은 상세를 실시간 조회시 키로 사용
	// 적재 중 ID 이관 및 비활성 매장 때문에 null 허용. null인 매장은 추천 조회에서 제외한다.
	@Column(unique = true, length = 100)
	private String googlePlaceId;

	// 최근 적재 목록에 있으면 true, 목록에서 빠지면 false 로 바꾼다.
	// API 는 active 인 매장만 보여준다.
	private boolean active;

}
