package com.odyssey.legaldong.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import com.odyssey.cell.entity.Area;

@Entity
@Immutable
@Table(name = "legal_dong")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalDong {
	@Id
	@Column(length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Area area;
}
