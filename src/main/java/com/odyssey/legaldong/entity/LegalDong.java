package com.odyssey.legaldong.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.odyssey.cell.entity.Area;

/** 법정동과 서비스 지역의 대응. 행정동이 아니라 법정동으로 지역을 정한다. */
@Entity
@Immutable
@Table(name = "legal_dong")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalDong {
	/** 법정동 이름(예: 인사동, 을지로3가). 현재 서비스 범위에서는 이름을 PK로 쓴다. */
	@Id
	@Column(length = 30)
	private String name;

	/** 종로·익선·인사동 등 화면의 지역 필터 값. 이 법정동에 속한 모든 칸이 공유한다. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Area area;
}
