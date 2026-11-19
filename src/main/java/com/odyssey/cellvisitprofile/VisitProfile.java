package com.odyssey.cellvisitprofile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;

/**
 * 한 칸의 한 요일 방문 프로필. 같은 칸·요일의 시간대별 상대 방문 높이다.
 *
 * <p>그래프를 그리는 상태는 시간대별 막대 네 개를 갖고 그중 100 인 막대가 정확히 하나다. 그리지 않는
 * 상태는 막대가 없다. DB 의 공개 검증이 1차로 보장하는 조건이고, 여기서는 깨진 값이 객체가 되지
 * 못하게 막는 방어용이다. 화면에 쓰는 이름과 척도는 모른다.
 *
 * @param bars 표시 순서(점심 → 심야)대로의 막대. 순서의 기준은 조회 SQL 이다
 */
public record VisitProfile(VisitProfileStatus status, List<VisitBar> bars) {

	private static final int PEAK_HEIGHT = 100;

	/**
	 * 시간대 하나의 막대.
	 *
	 * @param relativeHeight 0~100. 가장 높은 시간대가 100 이다
	 */
	public record VisitBar(TimeSlot slot, int relativeHeight) {
	}

	/**
	 * @throws IllegalStateException 상태와 막대가 맞지 않는 경우. 공개된 값이 깨졌다는 뜻이다
	 */
	public VisitProfile {
		Objects.requireNonNull(status, "status");
		bars = List.copyOf(bars);
		if (status.showsGraph() ? !hasFourSlotsWithOnePeak(bars) : !bars.isEmpty()) {
			throw new IllegalStateException("Invalid published visit profile");
		}
	}

	private static boolean hasFourSlotsWithOnePeak(List<VisitBar> bars) {
		return bars.size() == TimeSlot.values().length
			&& bars.stream().map(VisitBar::slot).distinct().count() == bars.size()
			&& bars.stream().filter(bar -> bar.relativeHeight() == PEAK_HEIGHT).count() == 1;
	}

	/** @return 강조할 시간대(막대가 100 인 시간대). 그래프를 그리지 않는 상태는 비어 있다 */
	public Optional<TimeSlot> peakSlot() {
		return bars.stream()
			.filter(bar -> bar.relativeHeight() == PEAK_HEIGHT)
			.map(VisitBar::slot)
			.findFirst();
	}
}
