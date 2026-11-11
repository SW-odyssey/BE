package com.odyssey.scorebatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * 현재 적재된 점수·지표 데이터의 버전 기록.
 * 표를 통째로 교체하므로 각 데이터 행 대신 표별 한 행에 버전과 계산 시각을 둔다.
 */
@Entity
@Immutable
@Table(name = "score_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreBatch {
	/** 대상 표 이름: cell_slot_score, cell_slot_metric, cell_month_metric. */
	@Id
	@Column(length = 50)
	private String name;

	/** analysis가 출력한 계산 버전. 추천 결과를 어떤 데이터로 만들었는지 추적할 때 쓴다. */
	@Column(nullable = false, length = 50)
	private String version;

	/** analysis가 내보낸 데이터 계산 시각. 같은 적재 묶음의 각 행에 반복 저장하지 않는다. */
	@Column(nullable = false)
	private LocalDateTime computedAt;
}
