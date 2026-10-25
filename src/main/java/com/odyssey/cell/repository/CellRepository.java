package com.odyssey.cell.repository;

import com.odyssey.cell.entity.Cell;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 칸 조회. 칸은 analysis 가 적재하고 BE 는 읽기만 해서,
 * JpaRepository 의 save · delete 가 열리지 않도록 Repository 를 상속해 필요한 메서드만 둔다.
 */
public interface CellRepository extends Repository<Cell, Long> {

	/**
	 * 칸 번호 목록에 해당하는 칸을 번호 순으로 준다. 추천 응답의 cells 에 쓴다 (#30: id 오름차순).
	 *
	 * <p>메서드 이름으로 쿼리가 만들어진다. OrderBy 뒤에 방향을 안 쓰면 오름차순이다.
	 * <pre>
	 * select * from cell
	 *  where cell_id in (...)
	 *  order by cell_id
	 * </pre>
	 *
	 * @param cellIds 비어 있으면 안 된다 (호출하는 쪽에서 먼저 막는다)
	 */
	List<Cell> findByCellIdInOrderByCellId(Collection<Long> cellIds);
}
