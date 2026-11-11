package com.odyssey.cellslotmetric.entity;

import java.io.Serializable;
import jakarta.persistence.*;
import lombok.*;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CellSlotMetricId implements Serializable {
	/** analysis가 정한 50m 칸 번호. CELL.cell_id를 참조한다. */
	@Column(name = "cell_id", nullable = false)
	private Long cellId;

	/** 방문일과 같은 달(1~12)의 지표를 고른다. */
	@Column(nullable = false)
	private int month;

	/**
	 * 요일 묶음 (월~목 / 금 / 토 / 일). 공휴일도 해당 요일로 분류한다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DayType dayType;

	/**
	 * 시간대
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TimeSlot timeSlot;

	/**
	 * 지표 이름. workerShare(직장) · localVisitShare(현지인 방문) · foreignVisitShare(외국인 방문)
	 */
	@Column(nullable = false, length = 50)
	private String metricKey;
}
