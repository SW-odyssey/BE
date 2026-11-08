package com.odyssey.scorebatch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "score_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreBatch {
	@Id
	@Column(length = 50)
	private String name;

	@Column(nullable = false, length = 50)
	private String version;

	@Column(nullable = false)
	private LocalDateTime computedAt;
}
