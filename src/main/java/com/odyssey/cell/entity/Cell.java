package com.odyssey.cell.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 50m 격자 한 칸
 * BE 는 읽기만 한다. 적재는 analysis 저장소가 한다
 */
@Entity
// 읽기 전용이라 값을 바꿔도 UPDATE 가 나가지 않는다
@Immutable
@Table(name = "cell")
// getCellId() 같은 getter 를 자동으로 만들어준다
@Getter
// JPA 는 DB 에서 읽은 값을 담을 빈 객체를 만들 때 인자 없는 생성자가 필요하다
// public 이면 아무 코드나 빈 Cell 을 만들 수 있고,
// private 이면 Store 의 LAZY 연관관계가 쓰는 프록시(상속 클래스)가 생성자를 못 부른다
// 둘 다 만족하는 가장 좁은 범위가 protected 이다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cell {
	@Id
	private Long cellId;

	/**
	 * U+데이터칸 번호, U+ 데이터가 들어오기 전에는 비어있음
	 */
	@Column(unique = true, length = 50)
	private String uplusCellId;

	/**
	 * 한칸이 속한 구역, 한칸은 동으로 정함
	 * area DB에 JONGNO 같은 글자가 그대로 저장 되도록 하게 함
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Area area;

	/**
	 * 동 이름
	 */
	@Column(nullable = false, length = 30)
	private String legalDong;

	/**
	 * 동 영문 표기
	 */
	@Column(nullable = false, length = 30)
	private String legalDongEn;

	/**
	 * 칸 중심 위도
	 */
	private double centerLat;

	/**
	 * 칸 중심 경도
	 */
	private double centerLng;

	/**
	 * 칸 남서쪽(좌측 하단) 모서리의 위도
	 */
	private double swLat;

	/**
	 * 칸 남서쪽(좌측 하단) 모서리의 경도
	 */
	private double swLng;

	/**
	 * 칸 북동쪽(우측 상단) 모서리의 위도
	 */
	private double neLat;

	/**
	 * 칸 북동쪽(우측 상단) 모서리의 경도
	 */
	private double neLng;
}
