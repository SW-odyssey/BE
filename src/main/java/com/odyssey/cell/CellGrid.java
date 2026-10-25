package com.odyssey.cell;

import java.util.ArrayList;
import java.util.List;

/**
 * 50m 격자 칸 번호 계산. analysis 의 neighbors_3x3 과 같은 식이다.
 *
 * <p>칸 번호는 analysis 가 EPSG:5179(미터 단위 좌표)로 만든다.
 * <pre>
 * cellId = (floor(x / 50) - 14000) × 15999 + (floor(y / 50) - 26000)
 *        =  열 번호(동서)          × 15999 +  행 번호(남북)
 * </pre>
 * 그래서 동쪽으로 한 칸 가면 +15999, 북쪽으로 한 칸 가면 +1 이다.
 */
public final class CellGrid {

	/** 동쪽 한 칸 = +15999, 서쪽 한 칸 = -15999 */
	private static final long EAST_WEST = 15999L;
	/** 북쪽 한 칸 = +1, 남쪽 한 칸 = -1 */
	private static final long NORTH_SOUTH = 1L;

	private CellGrid() {
	}

	/**
	 * 가운데 칸과 둘레 8칸, 모두 9칸 (약 150m 사각형).
	 *
	 * <p>칸 하나에는 가게가 적고, 같은 가게도 좌표가 옆 칸에 찍히는 일이 있어서 범위를 넓힌다.
	 * 격자 밖 번호도 나오지만 그 칸에는 가게가 없어서 조회 결과에 영향이 없다.
	 *
	 * <pre>
	 * 예) 81383966 →
	 *   81367968  81383967  81399966   (북)
	 *   81367967  81383966  81399965
	 *   81367966  81383965  81399964   (남)
	 *    (서)                 (동)
	 * </pre>
	 *
	 * @param cellId 가운데 칸
	 * @return 9칸의 번호. 순서는 서쪽 줄부터, 줄 안에서는 남쪽부터
	 */
	public static List<Long> neighbors3x3(long cellId) {
		List<Long> ids = new ArrayList<>(9);
		for (long dx = -1; dx <= 1; dx++) {        // 서, 제자리, 동
			for (long dy = -1; dy <= 1; dy++) {    // 남, 제자리, 북
				ids.add(cellId + dx * EAST_WEST + dy * NORTH_SOUTH);
			}
		}
		return ids;
	}
}
