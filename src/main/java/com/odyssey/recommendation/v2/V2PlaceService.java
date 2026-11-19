package com.odyssey.recommendation.v2;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;
import com.odyssey.store.repository.StorePlaceRepository;

/** 활성 매장의 기본 정보를 준다. 요청 값 형식 검증은 컨트롤러가 끝낸 뒤라 존재 여부만 본다. */
@Service
@Transactional(readOnly = true)
public class V2PlaceService {

	private final StorePlaceRepository repository;

	public V2PlaceService(StorePlaceRepository repository) {
		this.repository = repository;
	}

	/**
	 * @param id 장소 번호
	 * @return 활성 매장의 기본 정보
	 * @throws BusinessException PLACE_NOT_FOUND(404). 없는 번호이거나 비활성 매장
	 */
	public V2PlaceResponse place(long id) {
		return repository.findActiveById(id)
			.map(V2PlaceResponse::from)
			.orElseThrow(() -> new BusinessException(
				CommonErrorCode.PLACE_NOT_FOUND, "현재 제공하는 장소가 아닙니다."));
	}
}
