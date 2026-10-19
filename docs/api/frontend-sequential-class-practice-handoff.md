# 프론트 전달: 클래스 교정 문제 순차 수행

클래스의 한 `PRACTICE` 단계에서 예문을 하나만 선택하는 대신, API가 반환한 예문을 처음부터 끝까지 순서대로 모두 수행하도록 변경해 주세요. 전체 문제 수는 하드코딩하지 않고 `items.length`를 사용합니다.

백엔드 `feature/practice-content-expansion` 브랜치의 V44는 현재 억양 클래스 연습 단계마다 공개 예문 5개를 게시합니다. V45부터 한 예문 세트에 5~10개를 허용하므로 향후 클래스별 문제 수가 달라질 수 있습니다. **운영 배포와 V44·V45 migration 적용 여부는 별도로 확인해야 합니다.**

## 1. 연동 API

모든 요청은 로그인 사용자 기준이며 `Authorization: Bearer <accessToken>`이 필요합니다. 프론트에서는 기존 `/api/backend` 프록시를 경유합니다.

| 메서드 | 백엔드 경로 | 기능 |
| --- | --- | --- |
| GET | `/api/courses/{courseId}/steps` | 클래스 단계 목록 조회 |
| GET | `/api/courses/{courseId}/steps/{stepId}/practice-examples` | 해당 연습 단계의 예문 5~10개 조회 |
| GET | `/api/practice-examples/{exampleId}/audio` | 예문 음성 조회 |
| POST | `/api/training-sessions` | 현재 예문의 학습 세션 생성 |
| POST | `/api/training-sessions/{sessionId}/recordings/upload-url` | 녹음 업로드 URL 생성 |
| POST | `/api/training-sessions/{sessionId}/recordings` | 업로드한 녹음 등록 |
| POST | `/api/training-sessions/{sessionId}/analyze` | 현재 예문 분석 요청 |
| GET | `/api/training-sessions/{sessionId}/analysis/status` | 분석 상태 조회 |
| POST | `/api/training-sessions/{sessionId}/complete` | 현재 예문의 학습 세션 완료 |
| PATCH | `/api/courses/{courseId}/progress` | 클래스 단계 진행률 저장 |
| POST | `/api/courses/{courseId}/complete` | 클래스 전체 완료 |

## 2. 예문 조회 계약

```http
GET /api/courses/{courseId}/steps/{stepId}/practice-examples
```

응답 `data` 형식:

```ts
{
  courseId: number;
  stepId: number;
  revision: number;
  items: Array<{
    id: string;
    order: number;
    text: string;
    hint: string | null;
    focus: "PRONUNCIATION" | "INTONATION" | "BOTH" | null;
    locale: "ko-KR";
    practiceContentId: number;
  }>;
}
```

- `items`는 현재 5~10개이며 `order`는 1부터 `items.length`까지 연속됩니다.
- 화면 표시와 실행 순서는 배열 위치가 아니라 `order` 오름차순을 기준으로 합니다.
- 전체 문제 수, 진행률과 마지막 문제 여부는 모두 `items.length`로 계산합니다.
- 학습 세션 생성에는 문자열 `id`가 아니라 숫자 `practiceContentId`를 사용합니다.
- 같은 교정 흐름에서는 반환된 모든 문제가 동일한 `courseId`, `stepId`, `revision`을 사용합니다.

## 3. 사용자 진행 순서

1. 연습 단계 진입 시 예문 목록을 한 번 조회하고 `order` 오름차순으로 정렬합니다.
2. 선택 화면 없이 첫 번째 예문을 표시하고 진행 상태를 `1/{items.length}`로 표시합니다.
3. 현재 item의 `practiceContentId`와 클래스 `stepId`로 학습 세션을 생성합니다.
4. 기존 녹음 업로드, 녹음 등록, 분석 요청, 분석 상태 조회 절차를 진행합니다.
5. 분석 완료 후 현재 학습 세션의 완료 API를 호출합니다.
6. 마지막 문제가 아니라면 다음 item으로 이동해 새 학습 세션을 생성합니다.
7. `currentIndex === items.length - 1`인 마지막 문제 완료 후에만 클래스 진행률을 갱신하고, 마지막 필수 단계라면 클래스 완료 API를 호출합니다.

화면상으로는 하나의 교정 세션이지만, 서버에서는 예문마다 별도의 `trainingSessionId`와 `analysisId`가 생성됩니다.

## 4. 학습 세션 생성

```http
POST /api/training-sessions
Content-Type: application/json
```

```json
{
  "contentId": 123,
  "courseStepId": 45,
  "learningFocus": "INTONATION",
  "titleExamId": null
}
```

- `contentId`: 현재 item의 `practiceContentId`
- `courseStepId`: 예문 조회에 사용한 `stepId`
- `learningFocus`: item의 `focus`; null이면 연습 콘텐츠의 초점에 맞춰 `INTONATION` 사용
- 응답의 `sessionId`를 현재 문제의 녹음·분석·완료 요청에 사용합니다.

## 5. 프론트 상태

교정 화면에서는 최소한 다음 상태를 함께 관리해야 합니다.

```ts
type SequentialPracticeState = {
  courseId: number;
  stepId: number;
  revision: number;
  items: PracticeExample[];
  currentIndex: number;
  sessionIdsByOrder: Record<number, number>;
  completedOrders: number[];
};
```

- 다음 문제 버튼은 현재 학습 세션이 `COMPLETED`일 때만 활성화합니다.
- 앞 문제로 돌아가 결과를 볼 수는 있지만 완료된 문제의 세션을 자동 재생성하지 않습니다.
- 새로 녹음하기는 현재 문제의 기존 재녹음 흐름을 사용합니다.
- 빠른 연속 클릭으로 같은 order의 세션을 중복 생성하지 않도록 생성 요청 중 버튼을 잠급니다.
- 현재 백엔드는 교정 묶음 전용 ID를 제공하지 않으므로 새로고침 후 묶음 진행 상태 자동 복원은 이번 범위에 포함되지 않습니다.

## 6. 클래스 진도 처리

마지막 문제 전에는 `PATCH /api/courses/{courseId}/progress`를 호출해 해당 `PRACTICE` 단계를 완료 처리하지 않습니다.

`completedOrders.length === items.length`가 된 후:

```http
PATCH /api/courses/{courseId}/progress
Content-Type: application/json
```

```json
{
  "lastStepId": 45,
  "progressPercent": 100
}
```

해당 단계가 클래스의 마지막 필수 단계이면 이어서 다음 요청을 호출합니다.

```http
POST /api/courses/{courseId}/complete
```

`progressPercent`는 클래스 전체 단계 기준으로 계산해야 합니다. 위 `100`은 현재 억양 클래스처럼 해당 `PRACTICE` 단계가 마지막 단계인 경우의 예시입니다.

## 7. 오류 처리

| HTTP | 오류 코드/상황 | 프론트 처리 |
| --- | --- | --- |
| 404 | 예문 세트·단계·콘텐츠 없음 | 목록을 닫고 새로고침 안내 |
| 409 | `COURSE_CONTENT_MISMATCH` | 목록을 다시 조회하고 현재 흐름 중단 |
| 409 | `ANALYSIS_NOT_COMPLETED` | 완료 처리하지 않고 분석 상태 재조회 |
| 409 | 세션 상태 충돌 | 현재 세션 상세·분석 상태 재조회 |
| 429 | `TTS_RATE_LIMITED` | 음성 재생만 잠시 후 재시도; 녹음 진행은 허용 |
| 503 | `PRACTICE_EXAMPLES_UNAVAILABLE` | 연습 시작 차단 및 재시도 안내 |
| 503 | `TTS_UNAVAILABLE` | 텍스트 연습은 유지하고 음성 재생 재시도 제공 |

## 8. 구현 시 주의 사항

- 기존 “예문 중 하나 선택” UI를 제거하고 현재 문제와 `items.length`로 계산한 전체 문제 수를 표시합니다.
- 예문 전체를 한꺼번에 학습 세션으로 생성하지 않습니다. 현재 문제 진입 시 하나씩 생성합니다.
- 한 문제의 분석 실패가 나머지 문제를 완료한 것으로 처리되면 안 됩니다.
- 각 문제의 점수는 기존 분석 결과로 표시합니다. 이번 백엔드 계약에는 전체 문제 통합 점수 필드가 없습니다.
- 자유 연습, 뉴스, 문장, 승급 시험의 단일 콘텐츠 흐름은 변경하지 않습니다.

## 9. 확인 요청 사항

- 새로고침 후 진행 중인 5문제 묶음을 로컬 상태로 복원할지, 첫 문제부터 다시 시작할지 결정
- 전체 문제 완료 화면에서 개별 점수만 표시할지 프론트 계산 평균도 표시할지 결정
- 중간 이탈 시 완료된 문제를 다시 수행하도록 할지 다음 미완료 문제부터 이어갈지 결정
- 운영 환경에서 V44·V45 적용 후 클래스별 예문 수와 순서가 그대로 반환되는지 통합 확인
