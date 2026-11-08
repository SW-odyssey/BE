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
	@Column(name = "store_id", nullable = false)
	private Long storeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StoreBadge badge;
}
