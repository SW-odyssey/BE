package com.odyssey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// local 프로파일(docker compose 자동 기동, SQL trace 로그)을 피하려고 test 로 고정한다
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OdysseyApplicationTests {

	@Test
	void contextLoads() {
	}
}
