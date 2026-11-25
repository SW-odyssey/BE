# 다국어 매장 상호 검색

검색창과 자동완성은 `GET /api/v2/place-search`를 사용합니다. 매장 선택 시 응답의 `id`를
`GET /api/v2/places/{id}`에 전달합니다.

| 파라미터 | 입력 |
| --- | --- |
| `q` | 상호 또는 상호 일부. 공백만 입력하거나 80자를 넘기면 400입니다. |
| `lang` | 표시 언어. `ko`(기본), `en`, `zh`(간체), `ja`. 요청 언어 표기가 없으면 한국어 원본 상호를 반환합니다. |
| `limit` | 최대 반환 건수. 기본 6, 범위 1~30입니다. |

네 언어 상호를 모두 비교합니다. 대소문자·공백·전각 차이는 무시하며, 정확히 일치하는 상호,
시작 부분이 일치하는 상호, 나머지 부분이 일치하는 상호 순서로 반환합니다. 같은 순위에서는 요청 언어로
일치한 매장을 우선하고, 그 다음에는 매장 ID로 정렬합니다. 동명 매장은 각각의 ID로 반환합니다.

활성 매장이면 술집과 Google Place ID가 없는 매장도 검색 대상입니다. 위치, 추천 점수, 방문 시간대는
적용하지 않습니다. 개별 음식과 매장의 연결 정보는 이 API에서 제공하지 않습니다.

```http
GET /api/v2/place-search?q=gongpyeong&lang=en&limit=6
```

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 7,
        "name": "Gongpyeong Restaurant",
        "cellId": "101",
        "kind": "korean",
        "lat": 37.57,
        "lng": 126.98,
        "sinceDate": null,
        "googlePlaceId": null
      }
    ]
  },
  "error": null
}
```

일치하는 활성 매장이 없으면 200과 `{"success":true,"data":{"items":[]},"error":null}`을
반환합니다. 서버는 항상 그 시점의 DB 상호를 조회하므로 검색을 위해 정적 JSON을 다시 배포할 필요가 없습니다.
