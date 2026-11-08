package com.odyssey.store.entity;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StoreBadgeId implements Serializable {
	/** STORE의 DB ID. 인허가 관리번호나 구글 place ID가 아니다. */
	@Column(name = "store_id", nullable = false)
	private Long storeId;

	/** 공식 지정 종류. 보유 배지가 하나라도 있으면 개업일과 무관하게 노포 조건을 충족한다. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StoreBadge badge;
}
