package com.odyssey.cell.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.legaldong.entity.LegalDong;

@Entity
@Immutable
@Table(name = "cell")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cell {
	@Id
	private Long cellId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "legal_dong", nullable = false)
	private LegalDong legalDong;

	private double swLat;
	private double swLng;
	private double neLat;
	private double neLng;

}
