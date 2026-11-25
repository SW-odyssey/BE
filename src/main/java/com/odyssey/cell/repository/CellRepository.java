package com.odyssey.cell.repository;

import com.odyssey.cell.entity.Cell;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 칸 조회. 칸은 analysis 가 적재하고 BE 는 읽기만 해서,
 * JpaRepository 의 save · delete 가 열리지 않도록 Repository 를 상속해 필요한 메서드만 둔다.
 */
public interface CellRepository extends Repository<Cell, Long> {

	/** 지역 필터와 무관하게 서비스 전체 칸 중 하나에 포함되는지 확인한다. */
	@Query("""
		select count(c) > 0 from Cell c
		where :lat >= c.swLat and :lat <= c.neLat
		and :lng >= c.swLng and :lng <= c.neLng
		""")
	boolean containsPoint(@Param("lat") double lat, @Param("lng") double lng);
}
