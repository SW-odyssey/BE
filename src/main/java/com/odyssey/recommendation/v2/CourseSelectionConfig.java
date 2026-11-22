package com.odyssey.recommendation.v2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CourseSelectionConfig {
	@Bean
	CourseSelector courseSelector(CourseVisitChecker checker) {
		return new BandShuffleSelector(checker);
	}
}
