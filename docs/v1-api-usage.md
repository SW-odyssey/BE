# v1 추천 API 호출 조사

[BE #88](https://github.com/SW-odyssey/BE/issues/88)에서 v1 호출을 확인하고 전환 여부를 결정한다.

## 운영 호출 기록

2026-11-25 12:25~17:45(KST) Cloud Run 요청 로그에서 v1 호출 173건을 확인했다. 이 중 Node의 추천 목록 호출 168건은 12:31:48~12:42:08에 한 네트워크 출처에서 들어왔고 모두 HTTP 200이었다. 전체와 5개 지역의 점심 결과를 각각 0페이지부터 조회했다. 전체 조회는 124건이었고, 서로 다른 페이지는 42개였다. 같은 출처에서 약 28초 전에 `curl`로 v1 메타를 한 번 조회한 기록이 있다. 나머지 4건은 다른 출처의 PowerShell 요청이었다.

Node 호출과 PowerShell 4건은 일회성 점검이었다고 작업 요청자가 확인했다. 같은 출처에서 앞서 발생한 `curl` 요청도 Node 점검과 연관됐을 가능성이 있다. Cloud Run 요청 로그에는 실행 스크립트명과 담당자 정보가 없다.

## 분석 사용처

[분석 저장소 main `d5de619`](https://github.com/SW-odyssey/analysis/tree/d5de619f57ba4e0eaaebfd31c2807d204e485b36)의 v1 호출 경로를 확인했다.

- `collect_deployed`는 서비스 지역 전체를 대상으로 대표 요일과 네 시간대의 모든 추천 페이지를 수집한다. Python `httpx`를 쓰고 요청 조건에 `visitDate`, `category`, `density`도 포함한다.
- `collect_snapshots`는 선택한 조건에서 추천 카드에 쓸 응답을 수집한다. `probe_api`는 응답 스키마와 추천 조건을 점검한다. 두 함수 모두 `run_api`를 켠 경우에만 새 요청을 보낸다.
- 보관 노트북에서는 조건별 추천 검토가 `collect_snapshots`를 사용한다. 이전 실시간 추천 카드 검토와 페이지 수집 노트북도 실행 옵션을 켜면 v1을 직접 호출한다.

활성 노트북과 저장소의 자동 실행 설정에서 위 호출을 찾지 못했다. 분석용 v1 수집·진단 작업은 과거 분석용이며 다시 실행할 계획이 없음을 확인했다.

## 이후 지역 목록 점검

전체·지역별 추천 후보를 다시 점검할 때는 [v2 지역 탐색](https://github.com/SW-odyssey/BE/pull/96)을 사용한다. `/api/v2/meta`에서 배치 ID를 선택하고 `/api/v2/area-recommendations`에 `datasetId`, `weekday`, `slot`, `areaId`, `categories`, `page`, `size`를 보낸다. 0페이지부터 `hasNext`가 `false`인 페이지까지 조회한다.

이 경로는 선택한 지역과 종류에서 분석에 사용할 수 있는 칸(`AVAILABLE`)의 방문 백분위가 75 이상이고 Google Place ID가 있는 활성 매장을 반환한다. 분석 기준의 v3 적재 대상 목록은 [매장 내보내기 스크립트](https://github.com/SW-odyssey/analysis/blob/d5de619f57ba4e0eaaebfd31c2807d204e485b36/scripts/export/export_be.py)를 `--profile v3`으로 실행해 만든 `store.csv`에서 확인한다.

2026-11-25 운영 API에서 2025-12 배치, 수요일 점심, 음식점·카페, 페이지 크기 30으로 전체(`all`)와 명동(`myeongdong`)의 0·1페이지를 조회했다. 네 요청 모두 HTTP 200이었고 각 페이지에 30곳이 있었으며 같은 지역의 두 페이지 사이에 매장 ID 중복이 없었다. 전체 일치 수는 전체 515곳, 명동 66곳으로 각 지역의 두 응답에서 같았다.
