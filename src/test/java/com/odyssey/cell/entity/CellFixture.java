package com.odyssey.cell.entity;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트용 {@link Cell}.
 *
 * <p>Cell 은 읽기 전용 엔티티라 public 생성자와 setter 가 없다. 그래서 같은 패키지에서 protected 생성자로 만들고,
 * 필드는 리플렉션으로 채운다. DB 없이 Cell 이 필요한 단위 테스트에서 쓴다.
 */
public final class CellFixture {

	private CellFixture() {
	}

	/**
	 * 남서 · 북동 모서리만 정한 인사동 칸. 동 이름 · 중심 좌표는 아무 값이다.
	 */
	public static Cell cell(long cellId, double swLat, double swLng, double neLat, double neLng) {
		Cell cell = new Cell();
		ReflectionTestUtils.setField(cell, "cellId", cellId);
		ReflectionTestUtils.setField(cell, "area", Area.INSADONG);
		ReflectionTestUtils.setField(cell, "legalDong", "공평동");
		ReflectionTestUtils.setField(cell, "legalDongEn", "Gongpyeong-dong");
		ReflectionTestUtils.setField(cell, "centerLat", (swLat + neLat) / 2);
		ReflectionTestUtils.setField(cell, "centerLng", (swLng + neLng) / 2);
		ReflectionTestUtils.setField(cell, "swLat", swLat);
		ReflectionTestUtils.setField(cell, "swLng", swLng);
		ReflectionTestUtils.setField(cell, "neLat", neLat);
		ReflectionTestUtils.setField(cell, "neLng", neLng);
		return cell;
	}
}
