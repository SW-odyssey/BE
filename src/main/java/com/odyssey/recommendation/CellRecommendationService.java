package com.odyssey.recommendation;

import java.util.List;

import com.odyssey.cell.repository.CellRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추천 조건을 다시 계산하지 않고 요청한 칸의 경계만 읽는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CellRecommendationService {

	private final CellRepository cellRepository;

	/** 검증된 ID 목록을 조회한다. SQL IN 조건으로 중복을 제거하고 셀 ID순으로 반환한다. */
	public CellRecommendationResponse findCells(List<Long> cellIds) {
		return new CellRecommendationResponse(
			cellRepository.findByCellIdInOrderByCellId(cellIds).stream()
				.map(CellRecommendationResponse.CellItem::from).toList());
	}
}
