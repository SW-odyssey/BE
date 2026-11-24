package com.odyssey.cellvisitprofile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.cellvisitprofile.entity.VisitProfileStatus;

/**
 * 한 칸의 한 요일 방문 프로필. 같은 칸·요일의 시간대별 상대 방문 높이다.
 *
 * <p>막대는 없거나 시간대마다 하나씩 네 개다. 상태별 조건은 아래와 같고, DB 의 공개 검증이 1차로 보장한다.
 * 여기서는 깨진 값이 객체가 되지 못하게 막는 방어용이다. 화면에 쓰는 이름과 척도는 모른다.
 * <ul>
 *   <li>AVAILABLE: 네 막대 모두 값이 있고 100 이 정확히 하나</li>
 *   <li>NO_DISTINCT_PEAK: 없거나, 네 막대 모두 값이 있고 100 이 하나 이상</li>
 *   <li>INCOMPLETE_TIME_SLOTS: 없거나, 값이 없는 막대가 하나 이상이고 값이 있는 막대 중 100 이 하나 이상</li>
 *   <li>NO_POSITIVE_VISITS: 없음</li>
 * </ul>
 *
 * @param bars 표시 순서(점심 → 심야)대로의 막대. 순서의 기준은 조회 SQL 이다
 */
public record VisitProfile(VisitProfileStatus status, List<VisitBar> bars) {

	private static final int PEAK_HEIGHT = 100;

	/**
	 * 시간대 하나의 막대.
	 *
	 * @param relativeHeight 0~100. 가장 높은 시간대가 100 이다. 품질 검사를 통과하지 못한 시간대는 null 이다
	 */
	public record VisitBar(TimeSlot slot, Integer relativeHeight) {

		boolean isPeak() {
			return relativeHeight != null && relativeHeight == PEAK_HEIGHT;
		}
	}

	/**
	 * @throws IllegalStateException 상태와 막대가 맞지 않는 경우. 공개된 값이 깨졌다는 뜻이다
	 */
	public VisitProfile {
		Objects.requireNonNull(status, "status");
		bars = List.copyOf(bars);
		if (!(bars.isEmpty() ? status != VisitProfileStatus.AVAILABLE : barsFit(status, bars))) {
			throw new IllegalStateException("Invalid published visit profile");
		}
	}

	private static boolean barsFit(VisitProfileStatus status, List<VisitBar> bars) {
		boolean fourSlots = bars.size() == TimeSlot.values().length
			&& bars.stream().map(VisitBar::slot).distinct().count() == bars.size();
		long peaks = bars.stream().filter(VisitBar::isPeak).count();
		long missing = bars.stream().filter(bar -> bar.relativeHeight() == null).count();
		return fourSlots && switch (status) {
			case AVAILABLE -> missing == 0 && peaks == 1;
			case NO_DISTINCT_PEAK -> missing == 0 && peaks >= 1;
			case INCOMPLETE_TIME_SLOTS -> missing >= 1 && peaks >= 1;
			case NO_POSITIVE_VISITS -> false;
		};
	}

	/** @return 강조할 시간대(100 인 막대). AVAILABLE 이 아니면 비어 있다 */
	public Optional<TimeSlot> peakSlot() {
		if (status != VisitProfileStatus.AVAILABLE) {
			return Optional.empty();
		}
		return bars.stream()
			.filter(VisitBar::isPeak)
			.map(VisitBar::slot)
			.findFirst();
	}
}
