package com.odyssey.cell.repository;

import static com.odyssey.cell.entity.QCell.cell;
import static com.odyssey.legaldong.entity.QLegalDong.legalDong;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CellRepository {

	private final JPAQueryFactory query;

	/** 칸이 있는 지역마다 칸 중심 좌표의 평균을 준다. 칸이 없는 지역은 빠진다. */
	public List<AreaCenter> findAreaCenters() {
		NumberExpression<Double> averageCenterLat = cell.swLat.add(cell.neLat).divide(2.0).avg();
		NumberExpression<Double> averageCenterLng = cell.swLng.add(cell.neLng).divide(2.0).avg();

		return query
			.select(legalDong.area, averageCenterLat, averageCenterLng)
			.from(cell)
			.join(cell.legalDong, legalDong)
			.groupBy(legalDong.area)
			.fetch()
			.stream()
			.map(row -> new AreaCenter(
				row.get(legalDong.area), row.get(averageCenterLat), row.get(averageCenterLng)))
			.toList();
	}
}
