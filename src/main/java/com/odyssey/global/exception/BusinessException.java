package com.odyssey.global.exception;

/**
 * 비즈니스 규칙 위반을 나타내는 예외.
 *
 * <p>던질 때 오류 코드({@link ErrorType})와 사용자에게 보여줄 문구를 함께 지정한다.
 * HTTP 상태 코드는 오류 코드가 정한다. 오류 코드 목록은 {@link CommonErrorCode}.
 * {@link GlobalExceptionHandler} 가 이 예외를 받아
 * {@link com.odyssey.global.response.ApiResult} 형식으로 변환한다.
 *
 * <p>사용 예
 * <pre>{@code
 * throw new BusinessException(CommonErrorCode.PLACE_NOT_FOUND, "장소를 찾을 수 없습니다: 99999");
 * }</pre>
 *
 * <p>위 예외가 만들어 내는 응답 (HTTP 404)
 * <pre>{@code
 * {
 *     "success": false,
 *     "data": null,
 *     "error": {
 *         "code": "PLACE_NOT_FOUND",
 *         "message": "장소를 찾을 수 없습니다: 99999"
 *     }
 * }
 * }</pre>
 */
public class BusinessException extends RuntimeException {

	private final ErrorType errorType;

	/**
	 * @param errorType 오류 코드 (HTTP 상태 코드도 여기서 정해진다)
	 * @param message   사용자에게 보여줄 메시지
	 */
	public BusinessException(ErrorType errorType, String message) {
		super(message);
		this.errorType = errorType;
	}

	/**
	 * @return 오류 코드
	 */
	public ErrorType getErrorType() {
		return errorType;
	}
}
