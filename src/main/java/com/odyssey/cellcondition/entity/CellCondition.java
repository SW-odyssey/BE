package com.odyssey.cellcondition.entity;

import java.io.Serializable;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 분석 배치에서 적재한 격자·요일·시간대별 추천 조건. */
@Entity
@Immutable
@Table(name = "cell_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CellCondition {

	@EmbeddedId
	private Id id;

	@Enumerated(EnumType.STRING)
	@Column(name = "quality_status", nullable = false, length = 30)
	private CellQualityStatus qualityStatus;

	private Double visitorPercentile;

	@Embeddable
	@Getter
	@EqualsAndHashCode
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	public static class Id implements Serializable {

		@Column(nullable = false, length = 80)
		private String datasetId;

		@Column(nullable = false)
		private Long cellId;

		@Column(nullable = false, length = 3)
		private String weekday;

		@Column(nullable = false, length = 10)
		private String slot;
	}
}
