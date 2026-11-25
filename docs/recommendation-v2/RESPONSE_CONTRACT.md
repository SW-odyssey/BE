# 추천 v2 응답 필드 사용처

[#93](https://github.com/SW-odyssey/BE/issues/93)에서 2026-11-26 기준으로 [BE `b13ad9e`](https://github.com/SW-odyssey/BE/commit/b13ad9e)와 [FE `c82f602`](https://github.com/SW-odyssey/FE/commit/c82f602)를 대조했다. 현재 화면에서 읽지 않는 필드도 공개 응답에서 쓰는 소비자가 확인될 때까지 유지한다.

## 목록 두 경로의 total

| 경로 | `total` 의미 | 소비자가 쓸 값 |
| --- | --- | --- |
| `GET /api/v2/recommendations` | 좌표 기준 주변 추천이 이번에 반환한 수. 항상 `items.length`와 같고 최대 30이다. | 현재 FE는 종류 필터를 화면에서 한 번 더 적용한 뒤 표시할 `items.length`를 계산한다. 서버 `total`은 호환을 위해 유지한다. [목록 어댑터](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/live-recommendations.ts#L53-L71) |
| `GET /api/v2/area-recommendations` | 지역·방문 조건·매장 종류가 일치하는 전체 매장 수. 페이지를 자르기 전 값이다. | 전체 결과 수는 `total`, 이번에 받은 수는 `items.length`, 다음 페이지 요청 여부는 `hasNext`를 쓴다. `page`는 0부터 시작한다. 확인한 FE 기준 커밋에는 이 경로를 호출하는 화면이 없다. [탐색 계약과 테스트](https://github.com/SW-odyssey/BE/pull/96) |

운영 API에서 최신 공개 배치의 금요일 저녁을 조회했다. 을지로 주변 추천은 `total`과 `items.length`가 모두 30이었다. 전체 지역 탐색은 `size=1`로 조회한 0·1페이지 모두 `total=639`이고 페이지별 매장 ID가 달랐다. 0페이지의 `hasNext`는 `true`였다.

## 유지하는 필드와 근거

| 응답 | 현재 화면이나 검증에서 읽는 값 |
| --- | --- |
| 주변 추천과 장소 기본 정보 | `items`, `id`, `name`, `kind`, `lat`·`lng`, `cellId`, `googlePlaceId`는 목록·지도·장소 상세와 미디어 연결에 쓴다. `cellId`는 상세의 방문 그래프 요청에도 필요하다. [목록·상세 요청](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/live-recommendations.ts#L53-L97) · [장소 매핑](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/course-api.ts#L135-L154) |
| 코스 | `status`, `context.datasetId`, `seed`, `stops`, `skipped`, `interruption`을 FE가 응답 모델로 옮긴다. `seed`는 코스 다시 만들기와 링크에 쓰고, 누락·중단 정보는 화면에 표시한다. 정류장의 `position`, `slot`, `place`, `walkMinutes`, `hoursUnknown`도 읽는다. `place.distanceMeters`는 총 이동 거리와 구간별 직선 거리에 쓰므로 유지한다. [코스 매핑](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/course-api.ts#L157-L189) · [거리와 타임라인](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/components/CourseTimeline.tsx#L174-L199) · [총 이동 거리](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/components/CourseScreen.tsx#L66-L76) |
| 방문 그래프 | `status`, `graph`, `highlightSlot`, `bars[].slot`과 `bars[].relativeHeight`를 읽는다. FE는 막대 개수와 값 범위, 누락 값, 100인 막대와 강조 시간대가 일치하는지 검사한 뒤 그래프를 그린다. [검증 로직](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/visit-pattern.ts#L28-L69) · [FE 검증 테스트](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/tests/live-recommendations.test.ts#L107-L163) |

장소 상세와 상호 검색의 `sinceDate`는 유지한다. 상세 화면은 이 값을 매핑해 두지만 현재 렌더링에서는 읽지 않는다. 분석에서 이력으로 계산하는 개업일이고, 노포 표시 기준에 포함된 값이다. 상세 목 응답에도 키가 있어 삭제하면 기존 계약 비교가 달라진다. [FE 장소 매핑](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/src/features/discovery/data/course-api.ts#L135-L154) · [노포 기준](https://github.com/SW-odyssey/analysis/blob/d5de619f57ba4e0eaaebfd31c2807d204e485b36/docs/decision/%EB%B0%A9%ED%96%A5_%EA%B2%B0%EC%A0%95.md#L70) · [장소 목 응답](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/mock/v2/mappings/places.json#L1-L9)

## 제거 후보와 확인 범위

- 현재 FE에서 읽는 곳을 찾지 못한 값은 주변 추천의 `status`, `context`, `total`, `items[].distanceMeters`, 코스의 `context`에서 `datasetId`를 제외한 값과 `stops[].visitTime`, `leaveTime`, `candidateRank`, `reasons`, 방문 그래프의 `context`, `graph.metric`, `comparison`, `scaleMax`다. 주변 추천 거리와 코스 거리는 별개로 판단한다.
- 브라우저 화면 외에 FE 사진·요약 서버도 장소 상세를 호출한다. 사진은 `googlePlaceId`, 요약은 `name`, `googlePlaceId`, `lat`, `lng`를 읽는다. 최근 24시간의 운영 v2 성공 요청 표본 1,000건에는 브라우저 외에도 Node·CLI 호출이 있었다. 요청 로그로는 호출 주체나 읽은 JSON 필드를 확정할 수 없다. [서버 상세 요청](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/server/place-request.ts#L5-L9) · [사진](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/server/place-photos.ts#L90-L95) · [요약](https://github.com/SW-odyssey/FE/blob/c82f602ec1be316e4db7d1197aa1f5fb2def8488/server/place-summary.ts#L125-L137)
- 이번에 삭제를 확정한 필드는 없다. 후속 제거 시 실제 호출 주체와 전환 범위를 확인한 뒤 응답·Swagger·목 응답·계약 테스트를 함께 맞춘다. [기존 JSON 계약 비교](../../src/test/java/com/odyssey/contract/JsonDiff.java#L61-L76)
