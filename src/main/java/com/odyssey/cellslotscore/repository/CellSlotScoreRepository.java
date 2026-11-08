package com.odyssey.cellslotscore.repository;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.legaldong.entity.QLegalDong.legalDong;
import static com.odyssey.cellslotscore.entity.QCellSlotScore.cellSlotScore;

import java.util.List;
import org.springframework.stereotype.Repository;
import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.*;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CellSlotScoreRepository {
	private final JPAQueryFactory queryFactory;

	/** 후보 칸을 점수 내림차순, 동점이면 칸 번호 순으로 읽는다. */
	public List<RankedCell> findRankedCells(
		int month, DayType dayType, TimeSlot slot, Area area, DensityLevel density) {
		return queryFactory.select(Projections.constructor(RankedCell.class,
				cell.cellId, cellSlotScore.score))
			.from(cellSlotScore)
			.join(cellSlotScore.cell, cell)
			.join(cell.legalDong, legalDong)
			.where(cellSlotScore.id.month.eq(month),
				cellSlotScore.id.dayType.eq(dayType), cellSlotScore.id.timeSlot.eq(slot),
				area == null ? null : legalDong.area.eq(area), densityCondition(density))
			.orderBy(cellSlotScore.score.desc(), cell.cellId.asc()).fetch();
	}

	private BooleanExpression densityCondition(DensityLevel density) {
		if (density == null) {
			return null;
		}
		return density == DensityLevel.LIVELY
			? cellSlotScore.peakHour.isTrue() : cellSlotScore.densityLevel.eq(density);
	}
}
