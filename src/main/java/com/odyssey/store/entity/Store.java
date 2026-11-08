package com.odyssey.store.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import com.odyssey.cell.entity.Cell;
import java.time.LocalDate;

@Entity
@Immutable
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String mgmtNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cell_id", nullable = false)
	private Cell cell;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StoreKind kind;

	private double lat;
	private double lng;

	// 비활성 구 데이터는 이력 기준 개업일이 없을 수 있다.
	private LocalDate sinceDate;

	// 적재 중 ID 이관 및 비활성 매장 때문에 null 허용. 추천 조회에서 제외한다.
	@Column(unique = true, length = 100)
	private String googlePlaceId;

	private boolean active;

}
