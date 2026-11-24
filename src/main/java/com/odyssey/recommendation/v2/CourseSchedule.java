package com.odyssey.recommendation.v2;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.StoreKind;

/**
 * 코스 일정. 자리마다 시각·체류·받는 종류·필수 여부·점수 하한이 상수뿐이라 전략 클래스가 아니라 record 목록으로 둔다.
 * 일정을 바꾸려면 이 파일만 고친다. DB 나 Spring 을 모르는 순수한 값이다.
 *
 * <p>머무는 시간(분)은 통계청 생활시간조사 2024 원자료로 20·30대가 기타 실내 한 곳에 이동 없이 머문 구간의
 * 가중 중앙값이다(저자 계산, 공표 통계 아님). 식사 점심 50·저녁 60, 카페형 80, 오후 시작 쇼핑 30,
 * 21시 이후 시작 유흥 70. 자정을 넘긴 구간은 빼고 셌으므로 심야 값은 짧은 쪽으로 치우친다.
 */
final class CourseSchedule {

	static final int DINNER_MEAL_POSITION = 4;
	static final int SHOP_STAY_MINUTES = 30;
	static final double MIN_COURSE_PERCENTILE = 60;
	/** 목록 API 와 같은 기본 하한. */
	static final double DEFAULT_MIN_PERCENTILE = V2RecommendationSelector.MIN_VISITOR_PERCENTILE;

	enum AfternoonPreference {
		CAFE, SHOP, MIXED;

		static AfternoonPreference from(StoreKind kind) {
			return switch (kind) {
				case CAFE -> CAFE;
				case SHOP -> SHOP;
				default -> throw new IllegalArgumentException("afternoon: " + kind);
			};
		}
	}

	/** 자리를 못 찾았을 때 코스를 어떻게 할지. */
	enum Requirement {
		/** 못 찾으면 코스를 멈춘다. */
		REQUIRED,
		/** 못 찾으면 건너뛰고 다음 자리로 간다. */
		OPTIONAL,
		/** 요청이 원할 때만 넣고, 못 찾으면 건너뛴다. */
		OPT_IN;

		boolean skipWhenMissing() {
			return this != REQUIRED;
		}
	}

	/**
	 * 코스의 한 자리.
	 *
	 * @param position      일정 안의 자리 순번(1부터). 같은 시간대에 두 자리가 있을 수 있다
	 * @param slot          후보를 읽는 U+ 시간대
	 * @param visitTime     도착 시각(서울)
	 * @param stayByKind    받는 종류 → 그 종류일 때 머무는 시간(분)
	 * @param requirement   못 찾았을 때의 처리
	 * @param minPercentile 이 자리 후보의 칸 방문 백분위 하한
	 */
	record CourseSlot(int position, TimeSlot slot, LocalTime visitTime, Map<StoreKind, Integer> stayByKind,
		Requirement requirement, double minPercentile) {

		CourseSlot {
			stayByKind = Map.copyOf(stayByKind);
			if (stayByKind.isEmpty()) {
				throw new IllegalArgumentException("stayByKind");
			}
		}

		Set<StoreKind> kinds() {
			return stayByKind.keySet();
		}

		int stayMinutes(StoreKind kind) {
			Integer minutes = stayByKind.get(kind);
			if (minutes == null) {
				throw new IllegalArgumentException("kind: " + kind);
			}
			return minutes;
		}
	}

	private CourseSchedule() {
	}

	static List<CourseSlot> slots(Weekday weekday, StoreKind afternoon, TimeSlot startSlot, boolean lateNight) {
		return slots(weekday, AfternoonPreference.from(afternoon), startSlot, lateNight);
	}

	static List<CourseSlot> slots(Weekday weekday, AfternoonPreference afternoon, TimeSlot startSlot,
		boolean lateNight) {
		Map<StoreKind, Integer> afternoonStay = afternoon == AfternoonPreference.SHOP
			? Map.of(StoreKind.SHOP, SHOP_STAY_MINUTES) : Map.of(StoreKind.CAFE, 80);
		List<CourseSlot> schedule = List.of(
			required(1, TimeSlot.LUNCH, LocalTime.NOON, Map.of(StoreKind.KOREAN, 50)),
			required(2, TimeSlot.AFTERNOON, LocalTime.of(14, 0), afternoonStay),
			afternoon == AfternoonPreference.MIXED
				? afternoonSecond(weekday, Map.of(StoreKind.SHOP, SHOP_STAY_MINUTES))
				: afternoonPlay(weekday),
			required(DINNER_MEAL_POSITION, TimeSlot.DINNER, LocalTime.of(18, 0), Map.of(StoreKind.KOREAN, 60)),
			new CourseSlot(5, TimeSlot.DINNER, LocalTime.of(19, 30), Map.of(StoreKind.PLAY, 60),
				Requirement.OPTIONAL, DEFAULT_MIN_PERCENTILE),
			new CourseSlot(6, TimeSlot.LATE_NIGHT, LocalTime.of(22, 0), Map.of(StoreKind.BAR, 70),
				Requirement.OPT_IN, DEFAULT_MIN_PERCENTILE));
		return schedule.stream()
			.dropWhile(slot -> slot.slot() != startSlot)
			.filter(slot -> lateNight || slot.requirement() != Requirement.OPT_IN)
			.toList();
	}

	static CourseSlot afternoonPlay(Weekday weekday) {
		return afternoonSecond(weekday, Map.of(StoreKind.PLAY, 60, StoreKind.SHOP, SHOP_STAY_MINUTES));
	}

	private static CourseSlot afternoonSecond(Weekday weekday, Map<StoreKind, Integer> stayByKind) {
		double minimum = switch (weekday) {
			case SAT, SUN -> DEFAULT_MIN_PERCENTILE;
			default -> MIN_COURSE_PERCENTILE;
		};
		return new CourseSlot(3, TimeSlot.AFTERNOON, LocalTime.of(15, 40),
			stayByKind, Requirement.OPTIONAL, minimum);
	}

	private static CourseSlot required(int position, TimeSlot slot, LocalTime visitTime,
		Map<StoreKind, Integer> stayByKind) {
		return new CourseSlot(position, slot, visitTime, stayByKind, Requirement.REQUIRED, DEFAULT_MIN_PERCENTILE);
	}
}
