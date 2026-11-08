package com.odyssey.cellslotscore.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;

@Entity
@Immutable
@Table(name = "cell_slot_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellSlotScore {
	@EmbeddedId
	private CellSlotScoreId id;

	@MapsId("cellId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DensityLevel densityLevel;

	@Column(nullable = false)
	private boolean peakHour;

	@Column(nullable = false)
	private double score;

}
