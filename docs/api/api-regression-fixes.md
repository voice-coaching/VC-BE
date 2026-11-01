# 내부 이력 인증·추천 개수·OpenAPI 오류 수정

이슈 #150. 기존 경로와 정상 응답의 JSON 필드는 유지한다. DB migration과 프론트 변경은 없다.

## Direct History

`analysis.direct-history.enabled=true`일 때 내부 수신과 사용자 이력 경로가 활성화된다.

- `POST /api/internal/ai/direct-analysis-history`: 내부 콜백 Bearer 인증을 본문 처리보다 먼저 수행한다. 인증 누락·중복·위조는 401이며 `WWW-Authenticate: Bearer`, `Cache-Control: no-store`를 포함한다.
- 내부 수신은 기존 RunPod 오류 형식(`reasonCode`, `message`, `requestId`, `executionId`, `retryable`)을 사용한다. Content-Type 오류는 415, 1 MiB 초과는 413, 잘못된 JSON은 400, 문서 검증 실패는 422, 충돌은 409, DB/트랜잭션 장애는 503이다. 재시도 가능한 오류에는 `Retry-After: 1`을 포함한다.
- 내부 수신은 공개 OpenAPI에서 제외한다. 사용자 JWT 이력 조회·연결 3개 경로는 계속 공개 OpenAPI에 표시하며 공통 `ApiResponse`를 유지한다. 연결의 도메인 검증 실패와 충돌도 각각 422/409로 전달한다.

## 콘텐츠 추천

`GET /api/practice-contents/{contentId}/recommendations?limit=1`

선택 query `limit`을 DB 조회에 적용한다. 생략 시 5개이며 1 이상의 정수만 허용한다. 0·음수·정수가 아닌 값·Integer 범위 초과는 400이다. 실제 후보가 적으면 요청한 개수보다 적게 반환한다. 기존 상세 명세에는 상한이 없으므로 별도의 임의 상한을 추가하지 않았다.

추천 조건과 정렬은 유지하고, 캐시 키에 콘텐츠 ID와 요청 개수를 함께 사용한다. 이전의 콘텐츠 ID 단독 캐시는 새 요청에서 사용하지 않으며 기존 TTL에 따라 만료된다. 인증 필요 조건과 미존재/비공개 기준 콘텐츠의 404는 유지한다.

## OpenAPI

- 분석 구간의 중첩 `Item`에는 고유한 `AnalysisSegmentItem` 스키마 이름을 지정한다. JSON 필드와 실제 분석 응답은 유지한다. 알림 항목과 서로 다른 스키마를 참조하도록 생성 문서를 검증한다.
- 추천 query schema에 `limit`의 기본값 5와 최소값 1을 표시한다.
- `GET /api/practice-contents/next`의 기존 필수 `type`을 query schema의 `required`에도 표시한다. 런타임의 기존 필수 검사는 유지한다.

AI executor의 실제 추론 실패와 기존 결제 동시 조회 503은 별도 미해결 항목이며 이 수정의 완료 범위에 포함하지 않는다. 운영 적용은 PR 병합과 후속 배포 이후 확인해야 한다.
