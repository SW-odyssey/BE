package com.odyssey.cellslotmetric.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;

@Entity
@Immutable
@Table(name = "cell_slot_metric")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellSlotMetric {
	@EmbeddedId
	private CellSlotMetricId id;

	@MapsId("cellId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	@Column(nullable = false)
	private double value;

}
