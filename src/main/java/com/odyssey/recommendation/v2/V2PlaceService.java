package com.odyssey.recommendation.v2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odyssey.cellinsight.repository.CellInsightRepository;
import com.odyssey.cellinsight.repository.CellInsightRepository.*;
import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

@Service
@Transactional(readOnly = true)
public class V2PlaceService {
	private final CellInsightRepository repository;
	public V2PlaceService(CellInsightRepository repository) { this.repository = repository; }
	public Place place(long id) {
		if (id <= 0) throw V2Query.invalid("id는 양의 정수입니다.");
		return repository.place(id).orElseThrow(() -> new BusinessException(
			CommonErrorCode.PLACE_NOT_FOUND, "현재 제공하는 장소가 아닙니다."));
	}

}
