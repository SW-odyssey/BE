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
	@Column(name = "cell_id", nullable = false)
	private Long cellId;

	@Column(nullable = false)
	private int month;

	@Column(nullable = false, length = 50)
	private String metricKey;
}
