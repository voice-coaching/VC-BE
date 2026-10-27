# 프론트 전달: 기준 음성 ID 및 순차 연습 처리

## 작업 유형

- API 계약 확인 및 프론트 연동 정정
- 클래스 예문 순차 연습 UX 연동

## 콘텐츠별 음성 정책

| 콘텐츠 | 재생 방식 | 사용 API |
| --- | --- | --- |
| 아나운서 따라 읽기 | S3에 저장된 아나운서 기준 음성 | `/api/practice-contents/{contentId}/reference-audios` -> `/api/reference-audios/{audioId}/playback-url` |
| 일반 문장 연습 | 서버 TTS | `/api/practice-examples/{exampleId}/audio` |
| 뉴스 연습 | 서버 TTS | `/api/practice-examples/{exampleId}/audio` |
| 클래스 예문 | 서버 TTS 또는 콘텐츠에 연결된 기준 음성 정책 | 해당 콘텐츠 응답의 음성 정보에 따름 |

일반 문장 연습 화면에서 TTS 버튼을 누르면 `reference-audios` API가 아니라 기존 TTS API를 호출해야 합니다.

## 1. 기준 음성 ID 사용 방식

### 확인된 문제

아나운서 따라 읽기 01의 기준 음성은 DB에서 다음과 같이 연결되어 있습니다.

| 항목 | 값 |
| --- | --- |
| 콘텐츠 ID | `279` |
| 콘텐츠 제목 | `아나운서 따라 읽기 01` |
| 기준 음성 ID | `64` |
| speaker type | `ANNOUNCER` |
| 상태 | `PUBLISHED` |
| S3 object key | `reference-audios/announcer/오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다..m4a` |

현재 프론트에서 다음과 같이 오래된 음성 ID `54`를 요청하는 것이 확인되었습니다.

```http
GET /api/reference-audios/54/playback-url
```

이 요청은 현재 아나운서 01 콘텐츠의 기준 음성이 아니므로, 고정된 음성 ID를 사용하면 안 됩니다.

### 기준 음성 조회 계약

```http
GET /api/practice-contents/{contentId}/reference-audios
Authorization: Bearer <accessToken>
```

프론트는 응답 목록에서 현재 콘텐츠에 해당하는 기준 음성을 선택한 뒤, 응답으로 받은 `id`를 재생 URL 요청에 사용해야 합니다.

```http
GET /api/reference-audios/{audioId}/playback-url
Authorization: Bearer <accessToken>
```

권장 처리 흐름:

```text
콘텐츠 조회
-> reference-audios 목록 조회
-> speakerType=ANNOUNCER 및 primary=true 기준 음성 선택
-> 선택한 item.id로 playback-url 요청
-> 응답 playbackUrl을 audio source로 사용
```

`audioId`는 콘텐츠마다 달라질 수 있으므로 프론트 코드에 `54`, `64` 등의 값을 하드코딩하지 않습니다.

### 응답 예시

```json
{
  "result": true,
  "message": "기준 음성 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": 64,
        "speakerName": "아나운서",
        "speakerType": "ANNOUNCER",
        "durationMs": null,
        "primary": true
      }
    ]
  }
}
```

재생 URL 응답은 다음과 같이 사용합니다.

```json
{
  "result": true,
  "message": "기준 음성 재생 URL을 발급했습니다.",
  "data": {
    "audioId": 64,
    "playbackUrl": "<presigned-url>",
    "expiresAt": "2026-10-19T19:21:10+09:00"
  }
}
```

`playbackUrl`은 private S3 객체를 위한 제한시간 URL입니다. URL을 저장하거나 임의로 조합하지 말고, 필요할 때 API를 통해 발급받습니다.

## 2. 클래스 예문 순차 연습

### 핵심 요구사항

- 한 클래스의 연습 단계에는 5개 또는 10개의 예문이 포함될 수 있습니다.
- 프론트는 문제 수를 하드코딩하지 않습니다.
- 실제 응답의 `items.length`를 전체 문제 수로 사용합니다.
- 모든 예문을 `order` 오름차순으로 하나씩 수행합니다.
- 한 예문을 완료한 뒤에만 다음 예문으로 이동합니다.

### 예문 목록 API

```http
GET /api/courses/{courseId}/steps/{stepId}/practice-examples
Authorization: Bearer <accessToken>
```

응답 계약:

```ts
type PracticeExamplesResponse = {
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
};
```

주의사항:

- `items`는 5개일 수도 10개일 수도 있습니다.
- 화면의 전체 문제 수는 `items.length`로 계산합니다.
- 배열 인덱스가 아니라 `order` 오름차순으로 정렬합니다.
- 세션 생성에는 `id`가 아니라 현재 예문의 `practiceContentId`를 사용합니다.
- 같은 클래스 단계의 예문은 동일한 `courseId`, `stepId`, `revision`을 공유합니다.

### 예문별 연습 API 흐름

각 예문마다 다음 흐름을 반복합니다.

```text
1. 현재 예문 표시
2. 현재 예문의 practiceContentId로 연습 세션 생성
3. 녹음 업로드 URL 발급
4. 녹음 업로드 및 등록
5. 분석 요청
6. 분석 상태 조회
7. 분석 완료 확인
8. 현재 예문 완료 처리
9. 다음 order 예문으로 이동
```

사용 API:

```http
POST /api/training-sessions
POST /api/training-sessions/{sessionId}/recordings/upload-url
POST /api/training-sessions/{sessionId}/recordings
POST /api/training-sessions/{sessionId}/analyze
GET /api/training-sessions/{sessionId}/analysis/status
POST /api/training-sessions/{sessionId}/complete
```

세션 생성 예시:

```json
{
  "contentId": 123,
  "courseStepId": 45,
  "learningFocus": "PRONUNCIATION",
  "titleExamId": null
}
```

- `contentId`: 현재 예문의 `practiceContentId`
- `courseStepId`: 예문 목록을 조회한 `stepId`
- `learningFocus`: 현재 예문의 `focus`; 값이 없으면 클래스의 학습 초점을 사용

### 프론트 상태 예시

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

화면 진행률 예시:

```ts
const total = items.length;
const current = currentIndex + 1;
const progress = `${current}/${total}`;
```

`currentIndex === items.length - 1`인 예문을 완료했을 때만 해당 클래스 단계의 완료 처리를 진행합니다.

## 3. 오류 및 상태 처리

| 상황 | 프론트 처리 |
| --- | --- |
| 아나운서 기준 음성 목록에 선택 가능한 음성이 없음 | 아나운서 재생 버튼을 활성화하지 말고 오류 안내 |
| 일반 문장 또는 뉴스 TTS 발급 실패 | TTS 오류 안내 및 재시도 제공 |
| playback URL 발급 `404` | 현재 콘텐츠의 기준 음성 목록을 다시 조회 |
| 예문 목록이 비어 있음 | 연습을 시작하지 않고 콘텐츠 준비 오류 안내 |
| 분석 실패 | 현재 예문 재시도 제공, 완료된 예문으로 처리하지 않음 |
| 네트워크 재시도 | 현재 `order`와 세션 상태를 유지한 채 재요청 |
| 마지막 예문이 아님 | 다음 예문으로 이동 |
| 마지막 예문 완료 | 클래스 단계 진행률 갱신 및 다음 단계 처리 |

## 4. 백엔드 확인 사항

- 아나운서 01 콘텐츠 ID `279`에 연결된 기준 음성 ID는 `64`입니다.
- S3 object key는 다음 값으로 저장되어 있습니다.

```text
reference-audios/announcer/오늘 아침 서울의 기온은 어제보다 5도가량 낮아지며 초겨울에 가까운 날씨를 보였습니다..m4a
```

- S3 객체와 DB `object_key`는 일치합니다.
- 따라서 프론트는 기준 음성 ID를 하드코딩하지 않고 목록 API 응답에서 가져와야 합니다.
- 예문 수는 클래스 데이터에 따라 달라질 수 있으므로 API 응답의 `items.length`를 기준으로 처리합니다.
- 현재 문서의 API는 기존 백엔드 엔드포인트를 기준으로 하며, 프론트 연동을 위한 처리 규칙을 명확히 한 것입니다.

