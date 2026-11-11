package com.odyssey.cellmonthmetric.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.odyssey.cell.entity.Cell;

/**
 * 칸 × 달 단위의 방문 시간 패턴 비율.
 * analysis가 계산한 심야형 · 평일 낮형 · 주말 낮형 특징을 지표별 한 행으로 보관한다.
 */
@Entity
@Immutable
@Table(name = "cell_month_metric")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellMonthMetric {
	/** 같은 칸·달에도 지표가 여러 개이므로 지표 이름까지 키에 포함한다. */
	@EmbeddedId
	private CellMonthMetricId id;

	/** 지표가 속한 50m 칸. 복합 키의 cellId와 같은 FK를 공유한다. */
	@MapsId("cellId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	/**
	 * 해당 시간 패턴의 NMF 비율(0~1).
	 * 같은 칸·달의 세 지표 합은 1이며, 합 검증은 analysis 적재 과정에서 한다.
	 */
	@Column(nullable = false)
	private double value;
}
