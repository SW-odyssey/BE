package com.odyssey.recommendation.v2;

public enum WalkPreference {
	DEFAULT(150, 500, 100),
	SHORT(0, 300, 0);

	private final double minimumMeters;
	private final double radiusMeters;
	private final double avoidMeters;

	WalkPreference(double minimumMeters, double radiusMeters, double avoidMeters) {
		this.minimumMeters = minimumMeters;
		this.radiusMeters = radiusMeters;
		this.avoidMeters = avoidMeters;
	}

	CandidatePool.LegCandidates betweenStops(CandidatePool pool, CourseSchedule.CourseSlot slot) {
		return pool.forLeg(slot, minimumMeters, radiusMeters, avoidMeters);
	}
}
