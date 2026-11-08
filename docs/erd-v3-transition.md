# ERD v3 전환 순서

기준: BE main 8e1ad6c, analysis docs/decision/ERD_변경안.md (2026-11-08 v3 초안).
BE main에는 추천 API가 이미 있고 QueryDSL이 삭제 예정 열을 읽는다.
스키마를 먼저 완전히 바꾸면 컴파일 또는 실행이 깨지므로 다음처럼 분리한다.

## 1. V7 기반 구조 추가 (이 PR)

- LEGAL_DONG, STORE_BADGE, CELL_MONTH_METRIC, SCORE_BATCH와 읽기 전용 엔티티.
- STORE.kind, since_date 추가. 기존 매장 ID와 구 조회/적재용 열은 유지.
- LEGAL_DONG은 현 AOI에서 이름을 키로 사용. 범위 확대 시 코드 키 전환을 별도로 검토.
- kind는 기존 분류로 초기화한다. since_date는 재적재 전까지 null이다.
- 신규 테이블 PK, FK, 값 범위와 RLS 설정.
- 기존 조회와 meta 계약은 유지한다.

## 2. v3 데이터 및 추천 조회 전환

한 PR에서 마이그레이션, 엔티티, QueryDSL, 서비스와 테스트를 함께 맞춘다.

- CELL → LEGAL_DONG 조인으로 지역을 읽고 기존 CELL 열을 제거한다.
- 시간대 점수/지표 PK를 (cell_id, month, day_type, time_slot[, metric_key])로 바꾼다.
- DayType은 MON_THU/FRI/SAT/SUN으로 바꾸고 방문일의 달도 조회 조건에 넣는다.
- score 내림차순과 cell_id 동점 순서를 보장한다.
- '활기참' 조건은 density_level=LIVELY가 아니라 peak_hour=true로 바꾼다.
- StoreKind에서 API의 category/foodType을 만들고 새 개업일·배지 조건을 적용한다.
- 이전 점수는 월·요일 4분류를 복원할 수 없으므로 v3 결과를 재적재해야 한다.
  배포 전에 적재 파일을 검증하고 전환 시간 동안 추천 조회를 중지하거나 별도 DB에서 전환한다.
- STORE.id는 보존한다. 기존 행을 지우고 다시 만들지 않는다.
- 조회 전환 후 구 열, CELL_SCORE/CELL_METRIC을 제거한다.
- 3×3 확장 제거, 30곳 제한과 위치 기반 선별은 서비스/계약 테스트까지 함께 변경한다.
  meta의 maxResults와 노포 선택지도 같은 배포에 맞춘다.

## 적재 전에 해결할 계약

- v3 초안의 google_place_id NOT NULL과 load_v3.sql의 일시적 NULL 업데이트가 충돌한다.
  비활성 매장의 place ID 이관 정책까지 포함해 nullable + UNIQUE 여부를 analysis와 맞춘다.
  V7은 기존 nullable 컬럼을 유지하고 UNIQUE를 새로 걸지 않는다.
- 현 load_v3.sql은 구 STORE의 필수 열(category, franchise, road_addr 등)을 쓰지 않는다.
  V7 DB에 실행하지 않는다. 구 제약 제거와 조회 전환이 끝난 DB에서 검증한다.
- 구 적재기는 LEGAL_DONG/kind/since_date를 유지하지 않는다.
  v3 조회를 활성화하기 직전에 원본 재적재와 FK/중복/누락 검증이 필요하다.
- '노포 사용 여부' 요청 필드, 지도 칸 범위, 추천 응답의 칸 특징은 FE와 계약을 확정한다.
- load_v3.sql은 점수 표를 통째로 교체한다. 전환 전 백업과 재적재 파일을 확보한다.

## 3. 후속 API

지도 칸 API, 장소 상세, 채팅 추천은 각각 별도 PR로 진행한다.
채팅 모델과 스트리밍 여부는 ERD 준비 PR의 범위에 포함하지 않는다.
