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

## 설정 프로파일

- 프로파일을 정하지 않으면 `local` 로 동작합니다. docker compose 의 postgres 를 쓰고 SQL 로그를 켭니다.
- `supabase` 는 Supabase DB 에 연결합니다. 접속 정보는 환경변수로 넣습니다 (`.env.example` 참고).

## 개발 규칙

API · 패키지 · JPA · DB 스키마 · 테스트 · PR 규칙은
[docs/be-code-convention.md](docs/be-code-convention.md) 에 있습니다.

## 커밋 컨벤션

- `feat:` 기능 추가
- `fix:` 버그 수정
- `refactor:` 기능 변경 없는 코드 구조 개선
- `chore:` 설정/빌드 관련
- `test:` 테스트 작성, 수정
