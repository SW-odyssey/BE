# Odyssey - Backend

유동인구 빅데이터 처리 및 분석 기반 웹 서비스 (SW기업연계 프리캡스톤)

U+ 유동인구 데이터를 가공해 프론트엔드에 제공하는 조회 전용 API 서버입니다.

## 기술 스택

- Java 21 / Spring Boot 4.1.1 / Gradle
- Spring Web MVC, Spring Data JPA, PostgreSQL 17
- Flyway (DB 스키마 버전 관리)
- Lombok, Bean Validation
- springdoc-openapi (Swagger UI), Spring Boot Actuator

## 시작하기

Docker가 실행 중이어야 합니다. `bootRun` 이 `compose.yaml` 의 postgres 컨테이너를
자동으로 띄우고 DB 접속 정보까지 주입하므로, 별도 설정 파일 없이 바로 실행됩니다.

```bash
./gradlew bootRun
```

- API 서버: [http://localhost:8080](http://localhost:8080)
- API 문서: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- 헬스체크: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

```bash
./gradlew test          # 테스트
./gradlew build         # 빌드 (build/libs/*.jar)
```

## 폴더 구조

```
src/main/java/com/odyssey/
  global/                 # 공통 설정, 예외 처리, 응답 포맷
    config/               # CORS, Swagger, JPA 설정
    exception/            # 전역 예외 핸들러
    response/             # 공통 응답 래퍼
  domain/                 # 도메인 단위로 분리
    controller/         # REST 엔드포인트
    service/            # 비즈니스 로직
    repository/         # 조회 (JPA / JdbcClient)
    entity/             # JPA 엔티티
    dto/                # 요청·응답 DTO (record)

src/main/resources/
  application.yaml        # 공통 설정 (프로파일 미지정 시 local)
  application-local.yaml  # 로컬 - docker compose 기반, SQL 로그 on
  application-supabase.yaml # Supabase 연결 - 접속 정보는 환경변수
  db/migration/           # Flyway 마이그레이션 SQL (V1__init.sql ...)
```

## DB 스키마

스키마 변경은 **Flyway 마이그레이션 파일로만** 합니다.
`src/main/resources/db/migration/` 에 `V{번호}__{설명}.sql` 형식으로 추가하세요.

모든 프로파일이 `ddl-auto: validate` 라서 엔티티와 실제 테이블이 다르면 실패합니다.
엔티티를 고쳤다면 마이그레이션 SQL도 같이 커밋해야 합니다.

## API 규약

- 응답: `{ "success": true, "data": ..., "error": null }` 형태의 공통 래퍼
- 에러: HTTP 상태 코드 + `code` / `message`
- 조회 API는 모두 `GET`, 목록은 `page` / `size` 페이징

## 테스트

테스트는 **given / when / then** 으로 작성합니다.

- 주석 `// given`, `// when`, `// then` 으로 준비 · 실행 · 검증을 나눕니다.
- 테스트 하나에는 동작 하나만 둡니다. 여러 경우는 테스트를 나누거나 `@ParameterizedTest` 로 씁니다.
- 예외는 when 에서 `catchThrowableOfType` 으로 받고 then 에서 검증합니다.
- `@DisplayName` 은 구현 용어 대신 "어떤 상황이면 → 어떤 결과" 로 씁니다.

```java
@Test
@DisplayName("DB 조회가 실패하면 503 DATA_UNAVAILABLE 로 응답한다")
void dataAccess() throws Exception {
	// given
	MockHttpServletRequestBuilder request = get("/test/db");

	// when
	ResultActions result = mockMvc.perform(request);

	// then
	expectError(result, 503, "DATA_UNAVAILABLE", "데이터를 일시적으로 불러올 수 없습니다.");
}
```

## 커밋 컨벤션

- `feat:` 기능 추가
- `fix:` 버그 수정
- `refactor:` 기능 변경 없는 코드 구조 개선
- `chore:` 설정/빌드 관련
- `test:` 테스트 작성, 수정
