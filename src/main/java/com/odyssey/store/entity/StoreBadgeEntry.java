package com.odyssey.store.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 가게의 공식 노포 지정 한 건. 가게 하나가 여러 기관의 배지를 함께 가질 수 있다. */
@Entity
@Immutable
@Table(name = "store_badge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBadgeEntry {
	/** 매장 ID와 배지 종류의 조합. 같은 매장에 같은 배지를 중복 저장하지 않는다. */
	@EmbeddedId
	private StoreBadgeId id;

	/** 배지를 받은 매장. 복합 키의 storeId와 같은 FK를 공유한다. */
	@MapsId("storeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;
}
