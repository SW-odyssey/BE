# 최종 v2 API 계약

로컬 검증: `http://127.0.0.1:8084`. 운영 주소 확정·배포와 별개다. Swagger는 `/swagger-ui.html`, OpenAPI 문서는 `/v3/api-docs`다. 여기의 v3는 OpenAPI 규격 경로이며 제품 API는 `/api/v2`다.

## 다섯 호출

| GET | 필수 입력 | 선택 입력 | 목적 |
|---|---|---|---|
| /api/v2/meta | 없음 | 없음 | 실제 공개·지원 배치와 입력 enum |
| /api/v2/recommendations | datasetId,weekday,slot,lat,lng | category=food,kind=all,q=빈 문자열 | 직선300m 내 최대30매장, 서버 순서 유지 |
| /api/v2/cells/reasons | datasetId,weekday,slot,cellIds | 없음 | 현재 목록의 고유 셀 1~30개 이유 일괄 |
| /api/v2/cells/{cellId}/visit-pattern | datasetId,weekday | 없음 | 같은 셀·요일의 4시간대 방문 상대 패턴 |
| /api/v2/places/{id} | 양의 정수 path id | 없음 | 현재 활성 매장의 기본 정보 |

모든 입력 파라미터는 중복을 거부한다. 미지원 키도 400이다. `weekday=mon,tue,wed,thu,fri,sat,sun`, `slot=lunch,afternoon,dinner,late_night`다. 이유의 `cellIds`는 쉼표 구분 고유 양의 정수이며 JSON 객체 key와 응답 `cellId`는 문자열이다. 목록의 place `id`는 숫자다.

`category=food|cafe|all`, `kind=korean|bar|other|cafe|all`. 모순 조합은 400이다. q는 공백 정리 후 최대80자 이름 부분 검색이다. 좌표는 유한 WGS84값이며 위도±90·경도±180을 검사한다. 현재 시간에서 분석 월을 추정하거나 없는 배치를 다른 월로 대체하지 않는다.

## 공통 envelope와 context

| 필드 | 형식 / 사용 |
|---|---|
| success | boolean. HTTP 성공 여부와 함께 확인 |
| data | 성공 시 DTO, 실패 시 null |
| error | 성공 시 null, 실패 시 `{code:string,message:string}` |
| context.datasetId | meta.datasets[].id에서 선택한 불변 배치. 모든 근거·그래프 캐시의 핵심 |
| context.referenceMonth | YYYY-MM. 분석 기준 월 표시. 방문 예정 날짜와 구분 |
| context.weekday | 요청에 적용한 요일 enum. 그래프 상세 요일은 목록 요일과 독립 변경 가능 |
| context.slot | 목록·이유에만 존재. 그래프 요청·응답·캐시에는 넣지 않음 |
| context.cellId | 그래프에만 존재. 선택 매장의 cellId와 일치해야 함 |

## meta의 모든 필드

`datasets[] = {id,referenceMonth,population}`. population은 `DOMESTIC_20_39`; 언어별 표시명은 FE가 매핑한다. `weekdays[]`, `slots[]`, `categories[]`, `kinds[]`는 허용 enum 배열이다. datasets가 비면 선택 가능한 자료 없음 상태를 표시한다. 첫 배치를 선택하는 것은 FE 정책이며 사용자 선택을 조용히 바꾸지 않는다.

## recommendations의 모든 필드

| 필드 | 목적 / 주의 |
|---|---|
| status | OK 또는 NO_MATCHES. 후자는 정상 성공·빈 목록 |
| total | 이번에 반환한 items.length. 전체 후보 수·총 페이지 수 아님 |
| items[].id | 선택·상세조회·URL place 식별자. 이전 preview slug와 자동 호환 안 됨 |
| items[].name | 보유 매장명. 다국어 이름은 별도 콘텐츠 연결 필요 |
| items[].cellId | 이유 map join·방문 패턴 조회. 같은 셀 매장은 근거 공유 |
| items[].kind | korean/bar/other/cafe. 음식점/카페 배지·필터 enum |
| items[].lat,lng | 지도 마커·매장 위치. 사용자 위치가 아님 |
| items[].distanceMeters | 요청 lat/lng에서 매장까지 직선거리, 소수1자리. 도보거리 아님 |
| items[].googlePlaceId | 외부 장소 콘텐츠 연결 키. 지도·콘텐츠 연동에 사용. API 키가 아님 |

순서: 자격75 이상 → 방문 우선구간(80 이상 먼저) → 거리 → 내부 관리번호. 정밀 백분위 순 정렬이나 무작위 추출이 아니다. 최대30을 못 채워도 다른 조건으로 채우지 않는다. 내부 백분위·추정 인원·근무 비중·관리번호·실험 옵션은 공개하지 않는다.

## reasons의 모든 필드

`{context,reasonsByCellId:{[cellId]:{status,reasons:[{code}]}}}`. code 배열 순서가 표시 순서다. 서버는 문장·언어별 라벨을 반환하지 않는다. 데이터가 없더라도 요청한 셀 키는 상태와 빈 reasons 배열로 반환한다.

| code | FE 문구의 의미 |
|---|---|
| VISIT_RELATIVE_HIGH | 동일 월·요일·시간대 품질통과·방문양수 참조 셀 대비 방문 상대 위치 |
| DAY_DISTINCT | 월~목 중 다른 요일의 같은 시간대와 비교하는 특성 |
| STAY_DISTINCT | 방문 순위가 가까운 비교 셀 대비 체류 특성 |
| WALK_DISTINCT | 방문 순위가 가까운 비교 셀 대비 보행 특성 |
| AGE20_DISTINCT | 20·30대 내부 비교에서 20대 방문 특성 |
| AGE30_DISTINCT | 20·30대 내부 비교에서 30대 방문 특성 |
| TIME_PEAK | 요청 요일의 다른 시간대 대비 요청 시간대 방문 특성 |

참조 집합은 가까운300m 내 매장만이 아니다. ‘가까운 후보 중’이라고 바꾸지 않는다. 연령 문구는 매장 실제 고객 연령 비율을 뜻하지 않는다. 근무 긍정 문구·매장 인기 해석은 추가하지 않는다.

## visit-pattern의 모든 필드

`{context,status,graph}`. AVAILABLE일 때만 graph가 객체이고 나머지는 null이다.

| graph 필드 | 형식 / 화면 적용 |
|---|---|
| metric | visit 고정. FE ‘주변 방문 패턴’ 제목 |
| comparison | SAME_CELL_SAME_WEEKDAY. 같은 셀·요일 안 비교 안내 |
| scaleMax | 100. 자기 패턴의 최대값 기준이며 비중합100%가 아님 |
| highlightSlot | 4개 slot 중 하나. 해당 막대만 강조 |
| bars[].slot | 시간대 enum. FE 언어별 축 라벨로 매핑 |
| bars[].relativeHeight | 정수0~100. CSS 막대 높이로 사용. 절대 인원·매장 점수 아님 |

label·caption·title·description은 FE 템플릿에서 생성한다. 네 막대는 점심·오후·저녁·심야 순이다. 반올림5단계와 양수 최소1 규칙을 적용한 상대값이다. 미표시를 높이0으로 생성하지 않는다. 두 번째 이유 코드가 없다고 그래프를 숨기지 않는다.

## places의 모든 필드

`{id,name,cellId,kind,lat,lng,sinceDate,googlePlaceId}`. id/name/cellId/kind/lat/lng/googlePlaceId의 의미는 목록과 같다. sinceDate는 보유 이력의 기준 개업일이며 NULL 가능하다. 이 응답에 사진·소개·주소 문자열·AI 요약·리뷰·영업시간·다국어 이름은 없다. 별도 콘텐츠 공급자의 응답을 place id 또는 검증된 googlePlaceId로 연결해야 한다.

## 정상 상태와 오류를 분리

| 위치 | 값 | FE 처리 |
|---|---|---|
| 목록 | NO_MATCHES | 조건에 맞는 매장 없음. 네트워크 오류 아님 |
| 이유 | NOT_OBSERVED | 해당 조건 자료 없음 |
| 이유 | INCOMPLETE | 필요한 연령·성별 관측이 불완전 |
| 이유 | NO_POSITIVE_VISITS | 관측 방문 양수 근거 없음 |
| 이유 | NO_RECOMMENDATION_EVIDENCE | 품질은 통과했으나 추천 근거 기준 미달 |
| 이유 | CELL_NOT_IN_DATASET | 요청 셀이 해당 배치에 없음 |
| 그래프 | INCOMPLETE_TIME_SLOTS | 네 시간대 자료 불완전. 그래프 생성 금지 |
| 그래프 | NO_POSITIVE_VISITS | 관측 양수 방문 없음 |
| 그래프 | NO_DISTINCT_PEAK | 자료가 있어도 현행 피크 정책 미충족. 결측과 구분 |
| HTTP400 | INVALID_INPUT | 날짜변환·enum·중복키·미지원 조건 확인 |
| HTTP404 | DATASET_NOT_FOUND | 공개 배치 없음. meta 갱신 후 선택 안내 |
| HTTP404 | CELL_NOT_FOUND | 그래프 셀이 배치에 없음 |
| HTTP404 | PLACE_NOT_FOUND | 현재 제공하는 활성 장소가 아님 |
| HTTP503 | DATA_UNAVAILABLE | 지원하지 않는 내부 정책 버전 |
| 통신/서버 실패 | transport error / 5xx | 해당 영역 error·재시도. 정상 자료 없음과 합치지 않음 |

실제 응답 예시는 `examples/`의 격리 서버 캡처를 사용한다. 요청 URL의 datasetId는 meta에서 받은 현재 배치로 바꾼다.
