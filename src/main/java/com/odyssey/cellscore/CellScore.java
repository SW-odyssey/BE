package com.odyssey.cellscore;

import com.odyssey.cell.entity.Cell;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

import static jakarta.persistence.FetchType.*;

/**
 * 칸하나의 점수 entity로서 랭킹 API에 사용될 예정입니다.
 *
 * <p>analysis 가 적재할 때마다 통째로 교체됩니다.</p>
 * 점수 공식이 바뀌면 값과 version을 변경합니다.
 */
@Entity
@Immutable
@Table(name = "cell_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellScore {

	// PK 이면서 FK. @MapsId 가 cell 의 id 를 그대로 PK 로 쓴다
	@Id
	private Long cellId;

	// 부모로부터 PK를 가져옵니다. Cell.cellId
	@MapsId
	@OneToOne(fetch = LAZY)
	@JoinColumn(name = "cell_id")
	private Cell cell;

	// 점수.
	// ex) v0 은 주변 3×3 칸의 로컬(비프랜차이즈) 매장 수
	private Double score;

	// 순위. 같은 점수는 같은 순위로 처리합니다. (ex: 1, 2, 2, 4 ..)
	// 랭킹 대상이 아니면 비어 있을 수 있어 Wrapper 객체를 사용합니다.
	private Integer rank;

	// 랭킹 대상 여부.
	// 주변 매장이 너무 적은 칸(ex: n곳 미만)은 false처리
	private boolean eligible;

	// 어떤 공식으로 낸 점수인지 버전 기록
	// (예: v0-store-density)
	@Column(nullable = false, length = 50)
	private String version;

	// analysis 가 점수를 계산한 시각 (한국 시간)
	@Column(nullable = false)
	private LocalDateTime computedAt;

}
