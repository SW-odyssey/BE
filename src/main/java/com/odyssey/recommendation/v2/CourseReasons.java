package com.odyssey.recommendation.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseReason.AreaType;
import com.odyssey.recommendation.v2.CourseReason.LocalTop;
import com.odyssey.recommendation.v2.CourseReason.OpenDuringStay;
import com.odyssey.recommendation.v2.CourseReason.PeakNow;
import com.odyssey.recommendation.v2.CourseReason.SimilarChoices;

/**
 * 코스 정류장에 붙일 이유를 정한다. DB 나 Spring 을 모르는 순수한 계산이다.
 *
 * <p>어떤 이유를 어떤 순서로 붙일지는 이 클래스에만 있다. 순서는 LOCAL_TOP → OPEN_DURING_STAY → PEAK_NOW
 * → AREA_TYPE → SIMILAR_CHOICES 로 고정이고, 개수는 자르지 않는다(FE 가 앞의 둘만 보인다).
 */
final class CourseReasons {

	/** 비슷한 후보가 이만큼 있어야 SIMILAR_CHOICES 를 붙인다. 1곳이면 고른 곳뿐이다. */
	static final int MIN_SIMILAR = 2;

	/**
	 * 정류장 한 곳의 이유 재료.
	 *
	 * @param percentile  가게가 있는 칸의 방문 백분위(0~100)
	 * @param visitSlot   방문 시간대
	 * @param peakSlot    그 칸·요일의 가장 붐비는 시간대. 그래프가 없는 칸은 비어 있다
	 * @param areaType    그 칸의 동네 시간 유형. 정해지지 않은 칸은 비어 있다
	 * @param visitTime   도착 시각(HH:mm)
	 * @param leaveTime   나오는 시각(HH:mm)
	 * @param bandSize    1위와 3점 이내라 같이 섞은 후보 수
	 * @param hoursUnknown 정규 영업시간이 없어 영업을 확인하지 못했다
	 */
	record StopFacts(double percentile, TimeSlot visitSlot, Optional<TimeSlot> peakSlot,
		Optional<String> areaType, String visitTime, String leaveTime, int bandSize, boolean hoursUnknown) {

		StopFacts {
			Objects.requireNonNull(visitSlot, "visitSlot");
			Objects.requireNonNull(peakSlot, "peakSlot");
			Objects.requireNonNull(areaType, "areaType");
		}
	}

	private CourseReasons() {
	}

	static List<CourseReason> of(StopFacts facts) {
		List<CourseReason> reasons = new ArrayList<>();
		reasons.add(new LocalTop(topPercent(facts.percentile())));
		if (!facts.hoursUnknown()) {
			reasons.add(new OpenDuringStay(facts.visitTime(), facts.leaveTime()));
		}
		if (facts.peakSlot().filter(facts.visitSlot()::equals).isPresent()) {
			reasons.add(new PeakNow());
		}
		facts.areaType().ifPresent(type -> reasons.add(new AreaType(type)));
		if (facts.bandSize() >= MIN_SIMILAR) {
			reasons.add(new SimilarChoices(facts.bandSize()));
		}
		return List.copyOf(reasons);
	}

	/** 상위 몇 % 인지. 0% 로 보이지 않게 1 이상으로 올린다. */
	static int topPercent(double percentile) {
		return (int) Math.max(1, Math.round(100 - percentile));
	}
}
