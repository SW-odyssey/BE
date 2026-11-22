package com.odyssey.recommendation.v2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CourseSelectionConfig {
	@Bean
	CourseSelector courseSelector(CourseHoursLookup lookup) {
		return new VerifiedCourseSelector(new BeamSelector(), lookup);
	}
}
