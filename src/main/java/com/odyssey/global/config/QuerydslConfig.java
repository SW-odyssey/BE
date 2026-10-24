package com.odyssey.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * QueryDSL 쿼리를 만드는 JPAQueryFactory 를 빈으로 등록한다.
 *
 * <p>주입되는 EntityManager 는 트랜잭션마다 실제 EntityManager 로 이어지는 프록시라
 * 빈 하나를 여러 요청이 같이 써도 된다.
 */
@Configuration
public class QuerydslConfig {

	@Bean
	public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
		return new JPAQueryFactory(entityManager);
	}
}
