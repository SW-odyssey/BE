package com.odyssey.recommendation.v2;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({V2RecommendationController.class, V2VisitPatternController.class,
	V2CellReasonController.class, V2PlaceController.class, V2MetaController.class})
class V2ControllerTest {
	@Autowired MockMvc mvc;
	@MockitoBean V2RecommendationService recommendations;
	@MockitoBean V2VisitPatternService patterns;
	@MockitoBean V2CellReasonService reasons;
	@MockitoBean V2PlaceService places;
	@MockitoBean V2MetaService meta;

	@Test void graphUsesOnlyDatasetAndWeekday() throws Exception {
		mvc.perform(get("/api/v2/cells/10/visit-pattern").param("datasetId", "nov").param("weekday", "mon"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
		verify(patterns).profile("nov", "mon", 10);
	}

	@Test void graphRejectsRequestedSlotSoItCannotBecomeAnAccidentalCacheKey() throws Exception {
		mvc.perform(get("/api/v2/cells/10/visit-pattern").param("datasetId", "nov")
			.param("weekday", "mon").param("slot", "lunch"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		verifyNoInteractions(recommendations, patterns, reasons, places, meta);
	}

	@ParameterizedTest
	@CsvSource({"lat,NaN", "lng,Infinity", "radiusMeters,300", "limit,30", "weekday,mon_thu",
		"slot,evening", "density,lively", "ordering,visit_first", "workerPolicy,none"})
	void invalidAndLegacyConditionsAreRejected(String key, String value) throws Exception {
		var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
		params.set("datasetId", "nov"); params.set("weekday", "mon"); params.set("slot", "dinner");
		params.set("lat", "37.5"); params.set("lng", "127"); params.set(key, value);
		var request = get("/api/v2/recommendations").params(params);
		mvc.perform(request).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		verifyNoInteractions(recommendations, patterns, reasons, places, meta);
	}

	@Test void datasetIsMandatory() throws Exception {
		mvc.perform(get("/api/v2/recommendations").param("weekday", "mon").param("slot", "dinner")
			.param("lat", "37").param("lng", "127")).andExpect(status().isBadRequest());
	}
}
