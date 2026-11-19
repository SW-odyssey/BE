package com.odyssey.cell;

import com.odyssey.global.exception.BusinessException;
import com.odyssey.global.exception.CommonErrorCode;

/**
 * WGS84 좌표 한 점. 만들 때 위도·경도가 유한하고 범위 안인지 확인한다.
 *
 * <p>거리는 지구를 구로 보고 두 점을 잇는 직선(대원) 거리다. 도보 거리가 아니다.
 *
 * @param lat 위도. -90 이상 90 이하
 * @param lng 경도. -180 이상 180 이하
 */
public record GeoPoint(double lat, double lng) {

	private static final double EARTH_RADIUS_METERS = 6_371_000;

	public GeoPoint {
		if (!Double.isFinite(lat) || !Double.isFinite(lng)
			|| Math.abs(lat) > 90 || Math.abs(lng) > 180) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "유효한 위도·경도가 필요합니다.");
		}
	}

	/**
	 * 다른 점까지의 직선거리를 구한다.
	 *
	 * @return 미터. 반올림하지 않은 값이다
	 */
	public double distanceTo(GeoPoint other) {
		double deltaLat = Math.toRadians(other.lat - lat);
		double deltaLng = Math.toRadians(other.lng - lng);
		double haversine = Math.pow(Math.sin(deltaLat / 2), 2)
			+ Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat))
				* Math.pow(Math.sin(deltaLng / 2), 2);
		double clamped = Math.min(1, Math.max(0, haversine));
		return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(clamped));
	}
}
