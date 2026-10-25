# BE 코드 컨벤션

팀 규칙은 이 문서 하나에만 적는다. CodeRabbit 도 이 문서를 기준으로 리뷰한다 (`.coderabbit.yaml`).
코드를 보면 바로 알 수 있는 것은 적지 않고, 정한 규칙과 그 이유만 적는다.

## API

- **계약의 기준은 GitHub 이슈다.** 공통 규약은 #28, 기능별 명세는 #29 ~ #31.
  FE 명세서(PDF)와 다르면 서버 스키마(ERD, Flyway, 엔티티 enum)를 따른다. PDF 는 화면 흐름 참고용이다.
- **API 값은 서버 enum 이름의 소문자다.** `LATE_NIGHT` ↔ `late_night`. 변환표를 따로 두지 않는다.
  요청은 `ApiEnums.parse` 로 바꾸고, 응답은 Jackson 설정(`write-enums-to-lowercase`)이 바꾼다.
- **"전체 · 상관없음"은 요청에서 `all` · `any`, 서버 안에서는 `null` 이다.** 컨트롤러에서 바꾸고,
  서비스는 `"all"` 같은 문자열을 모르게 한다.
- **외부 입력은 컨트롤러에서 허용 값까지 검증한다.** 숫자이기만 하면 받지 말고 선택지 값만 받는다.
  예) `since` 는 20 · 30 · 40 만. 큰 수는 날짜 계산이나 DB 에서 500 이 나고, 음수는 조건이 의미를 잃는다.
- **응답은 `ApiResult` 로 감싼다** (`{ success, data, error }`). 오류는
  `BusinessException(CommonErrorCode, 사용자 문구)` 로 던지고, HTTP 상태는 오류 코드가 정한다.
- **결과가 없으면 오류가 아니라 200 과 빈 목록이다.**
- **목록은 페이징하지 않는다.** 200곳을 넘으면 잘라 주지 않고 400 `QUERY_SCOPE_TOO_WIDE` 를 낸다 (#28).
  FE 는 이 코드를 보고 "조건을 좁혀 주세요"를 띄운다.
- **"오늘"은 주입받은 `Clock` 으로 구한다** (`LocalDate.now(clock)`). 서버가 UTC 로 돌아도 서울 기준이 나오고,
  테스트에서 날짜를 고정할 수 있다.

## 패키지

- **도메인(`cell`, `store` …)으로 먼저 나누고, 그 안을 `entity` · `repository` 로 나눈다.**
- **기능(`recommendation` …)은 자기 패키지에 컨트롤러 · 서비스 · 요청/응답 record 를 둔다.**
- **의존은 기능 → 도메인 한 방향이다.** 도메인이 기능을 import 하거나, 기능끼리 import 하지 않는다.
- **클래스는 "무엇에 대한 지식인가"로 둔다.** 칸 번호 계산(`CellGrid`)은 추천에서만 쓰더라도 칸의 지식이라
  `cell` 에 둔다.
- **`global` 에는 도메인을 모르는 공통 기술만 둔다** (설정, 예외, 응답 형식). `util` 패키지는 만들지 않는다.

## JPA · 조회

- **BE 는 읽기만 한다.** 데이터는 analysis 저장소가 적재한다.
  엔티티는 `@Immutable` 이고 setter 가 없다. 기본 생성자는 `protected` 다 (LAZY 프록시가 상속해서 호출한다).
- **고정된 조회는 `Repository<T, ID>` 를 상속해 필요한 메서드만 연다.** `JpaRepository` 는 save · delete 가
  열려서 쓰지 않는다.
- **조건이 붙었다 빠지는 조회는 QueryDSL 로 쓴다.** 조건 메서드는 값이 없으면 `null` 을 돌려주고,
  `where` 에서 빠지게 한다. 조합마다 메서드를 만들지 않는다.
- **일부 열만 필요하면 record 프로젝션으로 읽는다.** 목록 카드에 엔티티 전체를 읽지 않는다.
- **결과 순서가 계약이면 메서드 문서에 적고 `order by` 로 보장한다.** Postgres 는 `order by` 가 없으면 순서를
  보장하지 않는다.
- **빈 목록으로 `in ()` 조회를 하지 않는다.** 호출하는 쪽에서 먼저 빈 결과를 돌려준다.
- **응답은 서비스 트랜잭션 안에서 다 만든다.** `open-in-view: false` 라 트랜잭션이 끝나면 LAZY 로딩이 안 된다.
  서비스 클래스에는 `@Transactional(readOnly = true)` 를 붙인다.

## DB 스키마

- **스키마 변경은 Flyway 마이그레이션 파일로만 한다** (`db/migration/V{번호}__{설명}.sql`).
  `ddl-auto: validate` 라서 엔티티를 고치면 마이그레이션도 같이 커밋해야 한다.
- **이미 적용된 V 파일은 고치지 않는다.** 새 번호로 추가한다. 고치면 체크섬이 달라져 기동이 실패한다.
- **새 테이블은 RLS 를 켠다** (`alter table … enable row level security`). Supabase 는 public 스키마를
  공개 API 로 내보내는데, 정책 없이 RLS 만 켜 두면 공개 API 로는 접근할 수 없다.

## 테스트

- **`// given`, `// when`, `// then` 주석으로 준비 · 실행 · 검증을 나눈다.**
- **테스트 하나에는 동작 하나만 둔다.** 여러 입력은 테스트를 나누거나 `@ParameterizedTest` 로 쓴다.
- **`@DisplayName` 은 구현 용어 대신 "어떤 상황이면 → 어떤 결과"로 쓴다.**
  예) "가게가 200곳을 넘으면 범위를 좁히라는 오류를 낸다"
- **예외를 직접 검증할 때는 when 에서 `catchThrowableOfType` 으로 받고 then 에서 검증한다.**
  `assertThatThrownBy` 로 when 과 then 을 섞지 않는다.
- **API 오류 응답 테스트는 HTTP 상태와 `error.code` · `error.message` 를 검증한다.**
- **계층마다 테스트 방식이 다르다.**

  | 계층 | 방식 |
  |---|---|
  | 컨트롤러 | `@WebMvcTest` + `@MockitoBean` 서비스 + 고정 `Clock`. 서비스에 넘긴 조건은 `ArgumentCaptor` 로 확인 |
  | 서비스 | Mockito 단위 테스트 (`BDDMockito` 의 `given` · `then`). DB 없이 분기 · 정렬을 본다 |
  | 리포지토리 | Testcontainers postgres. 엔티티가 읽기 전용이라 시드는 `JdbcClient` SQL 로 넣고 테스트마다 롤백 |

- **정렬 · 경계값 테스트는 그 코드를 지우면 깨져야 한다.** 입력 순서를 기대 순서와 다르게 두고
  (예: 시드를 id 역순으로 넣는다), 경계는 양쪽(200곳 통과, 201곳 오류)을 다 본다.

## 코드 스타일

- 들여쓰기는 탭이다 (`.editorconfig`). 한 줄은 100칸 안으로 쓴다.
- 커밋 전에 IntelliJ 기본 포매터로 Reformat Code + Optimize imports 를 한다.
- 주석은 코드가 무엇을 하는지보다 **왜 그렇게 했는지**를 적는다. 말투는 "~한다"로 맞춘다.
  설명은 그 설명이 필요한 메서드의 Javadoc 에 둔다.

## PR

커밋 컨벤션은 README 에 있다.

- **PR 본문은 템플릿(`.github/pull_request_template.md`)을 따르고, 처리하는 이슈를 `Closes #번호` 로 건다.**
- **merge 는 리뷰어 2명이 Approve 한 뒤에 한다.**
- **CodeRabbit 은 PR 을 만들 때만 자동으로 리뷰한다.** 이후 커밋을 다시 보게 하려면 PR 에
  `@coderabbitai review` 댓글을 남긴다.
