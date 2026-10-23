package com.odyssey.cell.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 칸 하나의 지표 한 개
 * 칸 × 지표 이름마다 한 줄이고, 지표가 늘어도 행만 추가된다
 * BE 는 읽기만 한다. 적재는 analysis 저장소가 한다
 */
@Entity
// 읽기 전용이라 값을 바꿔도 UPDATE 가 나가지 않는다
@Immutable
@Table(name = "cell_metric")
// getMetricKey() 같은 getter 를 자동으로 만들어준다
@Getter
// JPA 가 조회 결과를 담을 빈 객체를 만들 때 쓴다
// private 이면 LAZY 프록시(상속한 클래스)가 호출할 수 없어서 protected 로 둔다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellMetric {

	/**
	 * 행 번호. DB 가 자동으로 정한다
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 이 지표가 속한 칸
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	/**
	 * 지표 이름. storeCount3x3 같은 칸별 매장 수
	 */
	@Column(nullable = false, length = 50)
	private String metricKey;

	/**
	 * 지표 값. 값이 없으면 null
	 */
	private Double value;
}
