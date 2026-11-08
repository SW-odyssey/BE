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

/**
 * 시간대 점수로 칸을 고르는 조회.
 *
 * <p>지역 · 붐빔은 "전체 · 상관없음" 이면 조건에서 빠져야 해서 QueryDSL 로 조립한다.
 * where 에 null 을 넘기면 그 조건이 SQL 에서 사라진다.
 */
@Repository
@RequiredArgsConstructor
public class CellSlotScoreRepository {
	private final JPAQueryFactory queryFactory;

	/**
	 * 달 · 요일 묶음 · 시간대에서 조건에 맞는 후보 칸을 좋은 순서대로 준다.
	 *
	 * @param month   방문일의 달(1~12)
	 * @param dayType 월~목 / 금 / 토 / 일. 공휴일도 해당 요일로 분류한다
	 * @param slot    방문 시간대
	 * @param area    지역. null 이면 전체
	 * @param density 붐빔 선택. null 이면 상관없음, LIVELY는 peakHour로 거른다
	 * @return 칸 번호와 점수. 점수 내림차순, 동점이면 칸 번호순
	 */
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

	/** '활기참'은 상대 활동량이 높은 시간인지로, '덜 붐빔'은 지도 붐빔 등급으로 거른다. */
	private BooleanExpression densityCondition(DensityLevel density) {
		if (density == null) {
			return null;
		}
		return density == DensityLevel.LIVELY
			? cellSlotScore.peakHour.isTrue() : cellSlotScore.densityLevel.eq(density);
	}
}
