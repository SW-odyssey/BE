package com.odyssey.recommendation.v2;

import org.springframework.http.HttpStatus;
import com.odyssey.global.exception.ErrorType;

public enum V2Error implements ErrorType {
	DATASET_NOT_FOUND, CELL_NOT_FOUND;
	@Override public HttpStatus getStatus() { return HttpStatus.NOT_FOUND; }
}
