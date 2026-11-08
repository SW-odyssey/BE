package com.odyssey.cellmonthmetric.entity;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CellMonthMetricId implements Serializable {
	/** analysis가 정한 50m 칸 번호. CELL.cell_id를 참조한다. */
	@Column(name = "cell_id", nullable = false)
	private Long cellId;

	/** 방문일과 같은 달(1~12)의 특징을 고른다. 연도별 키는 두지 않는다. */
	@Column(nullable = false)
	private int month;

	/**
	 * lateNightShare(심야형), weekdayDayShare(평일 낮형), weekendDayShare(주말 낮형).
	 * 시간대별 지표와 달리 한 칸·달에 한 번만 계산한 시간 패턴이다.
	 */
	@Column(nullable = false, length = 50)
	private String metricKey;
}
