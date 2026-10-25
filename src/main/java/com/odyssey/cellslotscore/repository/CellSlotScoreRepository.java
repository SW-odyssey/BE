package com.odyssey.cellslotscore.repository;

import com.odyssey.cell.entity.Area;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.DensityLevel;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.cellslotscore.entity.QCellSlotScore.cellSlotScore;

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
	 * 요일 묶음 · 시간대에서 추천 대상(eligible)인 칸을 좋은 순서대로 준다.
	 *
	 * @param area         지역. null 이면 전체
	 * @param densityLevel 붐빔. null 이면 상관없음
	 * @return 칸 번호. 순위 오름차순, 순위가 같거나 없으면 칸 번호 순
	 */
	public List<Long> findRankedCellIds(
		DayType dayType, TimeSlot timeSlot, Area area, DensityLevel densityLevel) {
		return queryFactory
			.select(cellSlotScore.cell.cellId)
			.from(cellSlotScore)
			.join(cellSlotScore.cell, cell)        // 지역(area)은 cell 테이블에 있어서 조인한다
			.where(
				cellSlotScore.dayType.eq(dayType),
				cellSlotScore.timeSlot.eq(timeSlot),
				cellSlotScore.eligible.isTrue(),   // 활동량 하위 20% 칸은 추천하지 않는다 (analysis 가 정함)
				area == null ? null : cell.area.eq(area),
				densityLevel == null ? null : cellSlotScore.densityLevel.eq(densityLevel))
			// rank 1 이 가장 좋은 칸이라고 보고 오름차순. analysis 의 순위 방향은 아직 확인 전이다.
			// 같은 순위가 여러 칸이면 결과가 매번 같도록 칸 번호로 한 번 더 정렬한다
			.orderBy(cellSlotScore.rank.asc().nullsLast(), cell.cellId.asc())
			.fetch();
	}
}
