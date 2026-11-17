# 최종 추천 API v2

기존 대표 지표 전환 v2 실험은 폐기하고, 방문 고정 그래프·근무 긍정 문구 제외·순수 7요일의 분리형 v3를 최종 v2로 이름 변경했다. 코드와 상태를 반환하며 문장·다국어 라벨은 프론트에서 조립한다.

- [5개 GET API와 전체 필드·오류 계약](CONTRACT.md)
- [필요 테이블·키·NULL·3NF/BCNF 검토](ERD.md)
- [실제 계산·HTTP·DB 검증](VALIDATION.md)
- 화면별 연결·TypeScript client·발표: 분석 저장소의 `docs/recommendation-v2`, `contracts/recommendation-v2.ts`, `presentations/recommendation-v2`

`/api/v2/meta`, `/api/v2/recommendations`, `/api/v2/cells/reasons`, `/api/v2/cells/{cellId}/visit-pattern`, `/api/v2/places/{id}`를 제공한다. 기존 `/api/v1`과 테이블은 유지한다. 별도 v3 실험 작업트리를 병합할 필요가 없다. `/v3/api-docs`는 OpenAPI 규격 경로이므로 유지한다.

## 격리 실행

`.env.v2-local.example`을 `.env.v2-local`로 복사하고 로컬 전용 비밀번호를 설정한다. 로컬 파일은 Git에서 제외한다.

```powershell
docker compose --env-file .env.v2-local -f compose.v2.yaml up --build -d
```

HTTP는 `127.0.0.1:8084`, DB는 별도 볼륨의 `odyssey_v2_final_local`이다. 새 빈 DB에는 배치가 없으므로 meta.datasets가 비어 있는 것이 정상이다. 실제 데이터의 적재·전수 검증은 분석 저장소의 `load_v2_local.py`, `final_v2_acceptance.py`를 사용한다. 원본과 private bundle은 배포 이미지·Git에 포함하지 않는다.

```powershell
docker compose --env-file .env.v2-local -f compose.v2.yaml stop
```

V1~V8 이후 V9/V10으로 정규화 테이블을 추가한다. V10은 격리 검증에서 이미 적용된 V9 체크섬을 유지하면서 고정 population 열을 제거하기 위한 후속 마이그레이션이다. 운영 적용은 현재 스키마·기존 이력·DB 역할·권한을 확인한 별도 승인 작업이다.

## 공개 배치와 정규화

새로운 5개 테이블은 dataset, condition, reason, visit profile, visit bar다. 기존 cell/store/legal_dong 3개 테이블을 참조한다. 저장하지 않는 값은 서버 완성 문장, 고정 population 열, 미사용 근무 지표, 라벨·caption·중첩 그래프 JSON이다. `DOMESTIC_20_39`는 현재 API 고정 계약이다.

공개 직전 트리거는 AVAILABLE 프로필에 서로 다른 4개 시간대와 유일한 100 막대가 있는지 검사한다. 비표시 프로필은 막대가 없어야 한다. 공개 후 child 테이블 UPDATE까지 막는 트리거는 없으므로 배치 불변성은 로더·운영 권한 정책으로 유지해야 한다. 정정 배치는 새 datasetId로 발행한다.

실제 FE 연결, 사진·소개·주소·AI 요약 콘텐츠 API, Google Maps 전환, 운영 데이터 적재·배포는 이 PR에서 실행하지 않았다. 75/80/1.25는 시험 정책이며 사용자 만족도 최적값으로 입증하지 않았다.
