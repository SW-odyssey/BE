package com.odyssey.cell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CellGridTest {

	// 인사동 칸. 동쪽 한 칸 = +15999, 북쪽 한 칸 = +1
	private static final long CENTER = 81383966L;

	@Test
	@DisplayName("칸을 주면 자기 칸과 동서남북 · 대각선 8칸, 모두 9칸을 준다")
	void neighbors3x3() {
		// given
		long cellId = CENTER;

		// when
		List<Long> ids = CellGrid.neighbors3x3(cellId);

		// then: 서쪽 줄 = 81367967 ± 1, 가운데 줄 = 81383966 ± 1, 동쪽 줄 = 81399965 ± 1
		assertThat(ids).containsExactlyInAnyOrder(
			81367968L, 81383967L, 81399966L, // 북
			81367967L, 81383966L, 81399965L, // 가운데
			81367966L, 81383965L, 81399964L); // 남
	}

	@Test
	@DisplayName("이웃한 두 칸은 서로의 주변 9칸에 들어간다")
	void neighborsAreSymmetric() {
		// given: CENTER 의 남동쪽 칸
		long southEast = CENTER + 15999L - 1L;

		// when
		List<Long> aroundCenter = CellGrid.neighbors3x3(CENTER);
		List<Long> aroundSouthEast = CellGrid.neighbors3x3(southEast);

		// then
		assertThat(aroundCenter).contains(southEast);
		assertThat(aroundSouthEast).contains(CENTER);
	}
}
