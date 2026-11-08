package com.odyssey.cellslotmetric.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;

/**
 * 칸 × 달 × 요일 묶음 × 시간대 점수의 근거 지표 한 개
 * 지표 이름마다 한 줄이고, 지표가 늘어도 행만 추가된다
 * U+ 인구수 원본은 저장하지 않고 비율 지표만 둔다
 * BE 는 읽기만 한다. 적재는 analysis 저장소가 한다
 */
@Entity
// 읽기 전용이라 값을 바꿔도 UPDATE 가 나가지 않는다
@Immutable
@Table(name = "cell_slot_metric")
// getId() · getValue() 같은 getter 를 자동으로 만들어준다
@Getter
// JPA 가 조회 결과를 담을 빈 객체를 만들 때 쓴다
// private 이면 LAZY 프록시(상속한 클래스)가 호출할 수 없어서 protected 로 둔다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellSlotMetric {
	/** 칸 · 달 · 요일 묶음 · 시간대 · 지표 이름을 합친 키 */
	@EmbeddedId
	private CellSlotMetricId id;

	/**
	 * 이 지표가 속한 칸
	 */
	@MapsId("cellId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	/**
	 * 지표 값. 추천 대상 칸의 비율(0~1)만 적재한다.
	 */
	@Column(nullable = false)
	private double value;

}
