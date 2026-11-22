package com.odyssey.recommendation.v2;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.odyssey.cell.GeoPoint;
import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.recommendation.v2.CourseReasonFacts.Facts;
import com.odyssey.recommendation.v2.CourseSchedule.CourseSlot;
import com.odyssey.recommendation.v2.CourseSelector.Pick;
import com.odyssey.recommendation.v2.CourseSelector.Request;
import com.odyssey.recommendation.v2.CourseSelector.Selection;
import com.odyssey.recommendation.v2.CourseReasons.StopFacts;
import com.odyssey.recommendation.v2.V2CourseResponse.Stop;
import com.odyssey.store.entity.StoreKind;

/**
 * 요청한 일정의 각 자리에서 다음에 방문할 매장을 고른다.
 *
 * 코스를 다 고른 뒤 고른 칸들의 이유 재료를 한 번 읽어 정류장마다 이유를 붙인다.
 */
@Service
public class V2CourseService {

	private static final Logger log = LoggerFactory.getLogger(V2CourseService.class);

	private final CourseCandidates candidates;
	private final CourseSelector selector;
	private final CourseReasonFacts reasonFacts;

	V2CourseService(CourseCandidates candidates, CourseVisitChecker checker, CourseReasonFacts reasonFacts) {
		this(candidates, new BandShuffleSelector(checker), reasonFacts);
	}

	@Autowired
	V2CourseService(CourseCandidates candidates, CourseSelector selector, CourseReasonFacts reasonFacts) {
		this.candidates = candidates;
		this.selector = selector;
		this.reasonFacts = reasonFacts;
	}

	/** 같은 seed·배치·조건이면 같은 순서로 후보를 확인한다. 결과는 영업 확인 시점에 따라 달라질 수 있다. */
	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed) {
		return recommend(datasetId, weekday, startSlot, origin, excludeIds, seed, StoreKind.CAFE);
	}

	/** @param afternoon 오후 자리의 종류. 카페(CAFE) 또는 구경(SHOP) */
	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed, StoreKind afternoon) {
		return recommend(datasetId, weekday, startSlot, origin, excludeIds, seed, afternoon, false);
	}

	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot,
		GeoPoint origin, Set<Long> excludeIds, long seed, StoreKind afternoon, boolean lateNight) {
		return recommend(datasetId, weekday, startSlot, origin, excludeIds, seed, afternoon, lateNight,
			WalkPreference.DEFAULT);
	}

	public V2CourseResponse recommend(String datasetId, Weekday weekday, TimeSlot startSlot, GeoPoint origin,
		Set<Long> excludeIds, long seed, StoreKind afternoon, boolean lateNight, WalkPreference walk) {
		List<CourseSlot> slots = CourseSchedule.slots(weekday, afternoon, startSlot, lateNight);
		List<TimeSlot> timeSlots = slots.stream().map(CourseSlot::slot).distinct().toList();
		CandidatePool pool = candidates.load(datasetId, weekday, startSlot, timeSlots);
		Request request = new Request(slots, origin, excludeIds, weekday, seed, walk);
		Selection selection = selector.select(request, pool);
		return response(pool.context(), seed, selection);
	}

	private V2CourseResponse response(V2Context context, long seed, Selection selection) {
		List<Pick> picks = selection.picks();
		Facts facts = facts(context.datasetId(), context.weekday(), picks);
		List<Stop> stops = picks.stream().map(pick -> withReasons(pick, facts)).toList();
		String status = selection.interruption() == null ? "COMPLETE" : stops.isEmpty() ? "NO_MATCHES" : "PARTIAL";
		return new V2CourseResponse(status, context, seed, stops, selection.skipped(), selection.interruption());
	}

	/** 이유 때문에 코스가 실패하지 않게, 조회가 실패하면 그 재료로 붙는 이유(PEAK_NOW, AREA_TYPE)만 뺀다. */
	private Facts facts(String datasetId, Weekday weekday, List<Pick> picks) {
		if (picks.isEmpty()) {
			return Facts.NONE;
		}
		List<Long> cellIds = picks.stream().map(pick -> cellId(pick.stop())).distinct().toList();
		try {
			return reasonFacts.load(datasetId, weekday, cellIds);
		} catch (DataAccessException e) {
			log.warn("코스 이유 재료 조회 실패. 붐비는 시간대·동네 유형 이유를 뺀다: {}", e.getMessage());
			return Facts.NONE;
		}
	}

	private static Stop withReasons(Pick pick, Facts facts) {
		Stop stop = pick.stop();
		long cellId = cellId(stop);
		return stop.withReasons(CourseReasons.of(new StopFacts(pick.percentile(), stop.slot(),
			facts.peakSlot(cellId), facts.areaType(cellId), stop.visitTime(), stop.leaveTime(), pick.bandSize(),
			stop.hoursUnknown())));
	}

	private static long cellId(Stop stop) {
		return Long.parseLong(stop.place().cellId());
	}
}
