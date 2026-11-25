package com.odyssey.store.entity;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원본 상호와 함께 저장한 영어·간체 중국어·일본어 상호. */
@Entity
@Immutable
@Table(name = "store_name_translation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreNameTranslation {

	@Id
	private Long storeId;

	@Column(nullable = false, length = 100)
	private String sourceName;

	@Column(nullable = false, columnDefinition = "text")
	private String nameEn;

	@Column(nullable = false, columnDefinition = "text")
	private String nameZh;

	@Column(nullable = false, columnDefinition = "text")
	private String nameJa;
}
