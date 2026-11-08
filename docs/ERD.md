# ERD v1.2 + v3 전환 준비 (V7)

V7은 기존 추천 API가 쓰는 v1.2 구조를 유지하면서 아래 구조를 추가한다.
v3 전환 계획은 [erd-v3-transition.md](erd-v3-transition.md)에 있다.
아래 v1.2 다이어그램과 이 추가 다이어그램을 합친 것이 현재 스키마다.

```mermaid
erDiagram
    STORE ||--o{ STORE_BADGE : store_id
    CELL ||--o{ CELL_MONTH_METRIC : cell_id
    LEGAL_DONG {
        String name PK
        Area area
    }
    STORE {
        StoreKind kind "전환 중 null 가능"
        LocalDate sinceDate "이력 기준 개업일, 재적재 전 null"
    }
    STORE_BADGE {
        Long storeId PK,FK
        StoreBadge badge PK
    }
    CELL_MONTH_METRIC {
        Long cellId PK,FK
        int month PK
        String metricKey PK
        double value
    }
    SCORE_BATCH {
        String name PK
        String version
        LocalDateTime computedAt
    }
```

- `StoreKind`: CAFE, KOREAN, BAR, OTHER.
- `StoreBadge`: CENTURY_STORE, ORAE_GAGE, FUTURE_HERITAGE.
- `LEGAL_DONG`은 기존 CELL에서 한 번 채운다. 동일 이름의 지역이 충돌하면 V7은 실패한다.
  구 적재기와 호환하기 위해 CELL의 FK 연결과 area 제거는 후속 전환 때 한다.
- 기존 매장의 `kind`는 category/food_type으로 채운다. 구 적재기로 이후 추가·변경된
  매장에는 새 값이 없거나 오래된 값일 수 있으므로, 조회 전환 전에 v3 원본으로 다시 채운다.
- `since_date`는 analysis의 이력 계산 결과로 채워야 한다. 인허가일을 복사하지 않는다.
- 월별 지표의 month는 1~12, value는 0~1이다. 세 지표의 합 1은 적재 검증 대상이다.
- 새 테이블에는 모두 RLS를 켠다. SCORE_BATCH는 테이블별 적재 버전과 시각이다.
- 이 단계만으로 analysis의 `load_v3.sql`을 실행할 수 없다.

## 유지되는 v1.2 구조

대상: 음식점 · 카페 · 로그인 없음 · 조회 전용 API (2026-10-23 기준)

`[검토]` 는 아직 정하지 않은 것이다. v1.1 에서 바뀐 것은 [아래](#v11-에서-바뀐-것)에 있다.

```mermaid
erDiagram
    CELL ||--o{ STORE : "cell_id"
    CELL ||--o| CELL_SCORE : "cell_id"
    CELL ||--o{ CELL_METRIC : "cell_id"
    CELL ||--o{ CELL_SLOT_SCORE : "cell_id"
    CELL ||--o{ CELL_SLOT_METRIC : "cell_id"

    CELL {
        Long cellId PK "analysis 가 정한 칸 번호, 50m 칸 622개"
        String uplusCellId UK "[검토] U+ 데이터로 cellId 와 같은지 확인 후 삭제 여부"
        Area area
        String legalDong "인사동, 을지로3가"
        String legalDongEn "Insa-dong"
        double centerLat
        double centerLng
        double swLat
        double swLng
        double neLat
        double neLng
    }

    STORE {
        Long id PK "IDENTITY"
        String mgmtNo UK "인허가 관리번호, upsert 기준 [검토] googlePlaceId 를 주 키로"
        Long cellId FK
        String name
        String nameRomanized "null 가능"
        Category category
        FoodType foodType "null 가능, 카페면 null"
        String subType "null 가능, 인허가 업태 원본"
        boolean franchise
        String roadAddr
        String roadAddrEn "null 가능"
        double lat
        double lng
        LocalDate licenseDate
        String googlePlaceId "null 가능 [검토] 필수로"
        boolean active
    }

    CELL_SCORE {
        Long cellId PK, FK "[검토] 시간대 없는 기본 점수가 필요하면 유지"
        Double score "null 가능"
        Integer rank "null 가능, 랭킹 대상 아니면 비움"
        boolean eligible
        String version "v0-store-density"
        LocalDateTime computedAt
    }

    CELL_METRIC {
        Long id PK "IDENTITY"
        Long cellId FK "UK (cellId, metricKey)"
        String metricKey "storeCount3x3 등 칸별 매장 수"
        Double value "null 가능"
    }

    CELL_SLOT_SCORE {
        Long id PK "IDENTITY"
        Long cellId FK "UK (cellId, dayType, timeSlot)"
        DayType dayType
        TimeSlot timeSlot
        TargetGroup targetGroup "[검토] 화면에서 그룹을 고르면 UK 에 추가"
        DensityLevel densityLevel "null 가능, 추천 대상 아니면 비움"
        Double score "null 가능"
        Integer rank "null 가능"
        boolean eligible
        String version "v0-uplus-skeleton"
        LocalDateTime computedAt
    }

    CELL_SLOT_METRIC {
        Long id PK "IDENTITY"
        Long cellId FK "UK (cellId, dayType, timeSlot, metricKey)"
        DayType dayType
        TimeSlot timeSlot
        String metricKey "visitShare, stayShare, localContrast"
        Double value "null 가능"
    }
```

## enum (`@Enumerated(STRING)`)

| enum | 값 |
|---|---|
| `Area` | `JONGNO` · `IKSEON` · `INSADONG` · `EULJIRO` · `MYEONGDONG` |
| `Category` | `FOOD` · `CAFE` (비알코올 음료 · 빵/도넛 · 떡/한과 · 아이스크림/빙수) |
| `FoodType` | `KOREAN` (한식 · 분식) · `BAR` (주점 · 치킨호프) · `OTHER` |
| `DayType` | `WEEKDAY` · `WEEKEND` |
| `TimeSlot` | `LUNCH` (11~13) · `AFTERNOON` (14~17) · `DINNER` (18~21) · `LATE_NIGHT` (22~01) |
| `DensityLevel` | `LESS_BUSY` · `MODERATE` · `LIVELY` |
| `TargetGroup` [검토] | `DOMESTIC_20S` · `DOMESTIC_30S` · `DOMESTIC_40S` · `FOREIGN` |

## 정한 것

- **BE 는 읽기만 한다.** 적재는 analysis 저장소가 Supabase 에 직접 한다 (`scripts/export/` 는 매장 · 칸, `scripts/uplus/` 는 시간대 점수). 테이블 구조는 BE 의 Flyway 만 바꾼다.
- **Store 는 `mgmtNo` 로 upsert 하고 행을 지우지 않는다.** 목록에서 빠지면 `active=false`. 브라우저에 저장한 매장 id(`localStorage`)가 깨지지 않게 하려는 것이다.
- **점수 · 지표 테이블 4개는 적재 때마다 통째로 교체한다.** 점수 공식이 바뀌면 값과 `version` 만 바뀌고 테이블은 그대로다. 지표를 늘릴 때도 METRIC 테이블에 행만 추가한다. v1.0 은 `foodLq3x3` 처럼 공식이 열 이름에 박혀 있어서 공식이 바뀔 때마다 마이그레이션이 필요했다.
- **시간대 점수는 `CELL_SCORE` 에 열을 늘리지 않고 별도 테이블로 뒀다.** `CELL_SCORE` 는 `cellId` 가 PK 라 칸마다 한 행밖에 담지 못한다.
- **`densityLevel` 은 같은 요일 묶음 · 시간대 안에서의 상대 등급이다.** 활동량 하위 20% 칸을 추천 대상(`eligible`)에서 빼고, 남은 칸을 3등분한다. "덜 붐빔"은 사람이 없는 곳이 아니라 추천 대상 중 하위 3분의 1이다.
- **category 와 foodType 은 상가정보 업종으로 정하고, 짝이 없는 매장만 인허가 업태를 쓴다.** 인허가 업태만 쓸 때보다 실제와 더 잘 맞았다. 수치는 analysis `docs/decision/방향_결정.md`.
- **노포(20 · 30 · 40년 이상)는 `licenseDate` 로 계산한다.** 그래서 열이 따로 없다.
- **area 는 칸의 법정동으로 정한다.** 행정동은 대부분 "종로1·2·3·4가동"이라 구분이 안 된다. 칸의 법정동은 경계 파일 없이 칸 안 상가들의 법정동 다수결로 정한 근사값이다.
- **저장하지 않는 것:** U+ 인구수 원본(비율 지표만 저장), 구글 · 카카오 응답 내용(구글 `place_id` 만 예외), 사용자 정보. 저장 목록과 언어 설정은 브라우저 `localStorage`.

## v1.1 에서 바뀐 것

| 대상 | 변경 | 이유 |
|---|---|---|
| `CELL_SLOT_SCORE` · `CELL_SLOT_METRIC` | 추가 | U+ 인구 데이터로 요일 묶음 · 시간대별 칸 점수와 그 근거 지표를 낸다 |
| `STORE.foodType` | 추가 | 음식점 하위 선택(한식 · 술집) |
| `STORE.verifiedAt` | 삭제 | 카카오 정보는 서비스에 쓰지 않는다. 카카오 결과 저장은 정책상 금지다 |
| `STORE.category` | 출처 변경 | 인허가 업태 → 상가정보 업종 (없으면 인허가) |
| `CELL_METRIC` 의 LQ 키 | 행 삭제 | LQ 분석을 중단했다. 테이블 구조는 그대로 |

## 미정

- 화면에서 그룹(20 · 30 · 40대 · 외국인)을 고르는가: 고르면 `targetGroup` 을 `CELL_SLOT_SCORE` 의 UK 에 넣는다. 지금 analysis 출력에는 이 열이 없다
- 시간대 없는 기본 점수(`CELL_SCORE`)가 계속 필요한가
- 요일을 평일 / 주말로 묶을지, 7일을 그대로 둘지 (`DayType`)
- STORE 의 주 키를 `mgmtNo` 에서 구글 `place_id` 로 바꿀지. 바꾸면 브라우저에 저장된 매장 id 를 어떻게 이어 갈지도 정해야 한다
- `uplusCellId`: U+ 데이터를 받아 `cellId` 와 같으면 삭제
- 명동 칸을 랭킹에 넣을지 (지금 데이터는 포함)
- `nameRomanized` · `roadAddrEn` · `googlePlaceId` 채우는 방법
