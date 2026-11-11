package com.odyssey.cellslotscore.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;

/**
 * 칸 × 달 × 요일 묶음 × 시간대 점수
 * U+ 인구 데이터로 계산한다
 * BE 는 읽기만 한다. 적재는 analysis 저장소가 한다
 */
@Entity
// 읽기 전용이라 값을 바꿔도 UPDATE 가 나가지 않는다
@Immutable
@Table(name = "cell_slot_score")
// getScore() 같은 getter 를 자동으로 만들어준다
@Getter
// JPA 가 조회 결과를 담을 빈 객체를 만들 때 쓴다
// private 이면 LAZY 프록시(상속한 클래스)가 호출할 수 없어서 protected 로 둔다
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellSlotScore {
	/** 칸 · 달 · 요일 묶음 · 시간대를 합친 키. 대리 행 번호는 쓰지 않는다. */
	@EmbeddedId
	private CellSlotScoreId id;

	/**
	 * 이 점수가 속한 칸
	 */
	@MapsId("cellId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	/**
	 * 붐비는 정도. 추천 대상 칸만 적재하므로 null이 아니다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DensityLevel densityLevel;

	/**
	 * 이 시간 활동량을 자기 칸의 그 달 16개 시간대 평균으로 나눈 배율이,
	 * 같은 달·요일·시간대의 후보 칸 중 상위 1/3인지. '활기참' 필터에 쓴다.
	 * 지도 붐빔 등급인 densityLevel과는 다른 기준이다.
	 */
	@Column(nullable = false)
	private boolean peakHour;

	/**
	 * 같은 달 · 요일 묶음 · 시간대 안에서 현지인 방문 인원 순위를 백분위(0~100)로 바꾼 값
	 */
	@Column(nullable = false)
	private double score;

}
