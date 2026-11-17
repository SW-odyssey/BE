package com.odyssey.recommendation.v2;

import java.util.Set;
import org.springframework.util.MultiValueMap;

final class V2RequestValidation {
	private V2RequestValidation() {}
	static void check(MultiValueMap<String, String> params, Set<String> allowed) {
		params.forEach((key, values) -> {
			if (!allowed.contains(key) || values.size() != 1) {
				throw V2Query.invalid("지원하지 않거나 중복된 요청 조건: " + key);
			}
		});
	}
}
