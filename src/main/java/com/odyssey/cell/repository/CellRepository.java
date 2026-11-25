package com.odyssey.cell.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.odyssey.cell.entity.Cell;

public interface CellRepository extends JpaRepository<Cell, Long> {

	@Query("""
		select new com.odyssey.cell.repository.AreaCenter(
			c.legalDong.area, avg((c.swLat + c.neLat) / 2), avg((c.swLng + c.neLng) / 2))
		from Cell c
		group by c.legalDong.area
		""")
	List<AreaCenter> findAreaCenters();
}
