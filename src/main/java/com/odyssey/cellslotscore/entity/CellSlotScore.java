package com.odyssey.cellslotscore.entity;

import com.odyssey.cell.entity.Cell;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 칸 × 요일 묶음 × 시간대 점수
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

	/**
	 * 행 번호. DB 가 자동으로 정한다
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 이 점수가 속한 칸
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	/**
	 * 요일 묶음 (평일, 주말)
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DayType dayType;

	/**
	 * 시간대
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TimeSlot timeSlot;

	/**
	 * 붐비는 정도. 추천 대상이 아니면 null
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private DensityLevel densityLevel;

	/**
	 * 점수. 값이 없으면 null
	 */
	private Double score;

	/**
	 * 같은 요일 묶음, 시간대 안에서의 순위. 값이 없으면 null
	 */
	private Integer rank;

	/**
	 * 추천 대상인지. 활동량 하위 20% 칸은 false
	 */
	private boolean eligible;

	/**
	 * 점수 계산 방식의 버전. 예: v0-uplus-skeleton
	 */
	@Column(nullable = false, length = 50)
	private String version;

	/**
	 * 점수를 계산한 시각
	 */
	@Column(nullable = false)
	private LocalDateTime computedAt;
}
