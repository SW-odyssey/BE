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
	@Column(name = "cell_id", nullable = false)
	private Long cellId;

	@Column(nullable = false)
	private int month;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DayType dayType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TimeSlot timeSlot;

	@Column(nullable = false, length = 50)
	private String metricKey;
}
