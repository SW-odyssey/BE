package com.odyssey.store.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "store_badge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBadgeEntry {
	@EmbeddedId
	private StoreBadgeId id;

	@MapsId("storeId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_id", nullable = false)
	private Store store;
}
