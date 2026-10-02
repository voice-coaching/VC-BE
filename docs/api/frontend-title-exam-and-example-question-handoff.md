# 승급 시험 API 및 예시문제 콘텐츠 작업 완료 공유

백엔드에서 프론트 연동을 위해 아래 두 가지 작업을 완료했습니다.

1. 승급 시험 API 정합성 보강
2. 난이도별 예시문제 콘텐츠 추가

승급 시험 API는 프론트에서 요청한 아래 4개 API 기준으로 정리되어 있습니다.
예시문제 콘텐츠는 신규 API가 아니라 기존 학습 콘텐츠 조회 API를 통해 조회할 수 있도록 DB seed를 추가했습니다.

---

# 1. 승급 시험 기능 명세

## 1.1 엔드포인트

모든 요청은 로그인 사용자 기준이며 `Authorization: Bearer <accessToken>`이 필요합니다.

| 메서드 | 백엔드 경로 | 기능 |
| --- | --- | --- |
| GET | `/api/users/me/title` | 현재 칭호·누적 연습 횟수·응시 가능 여부 조회 |
| POST | `/api/users/me/title-exams` | 다음 칭호 승급 시험 생성 |
| GET | `/api/users/me/title-exams/{examId}` | 시험 상태 조회 |
| POST | `/api/users/me/title-exams/{examId}/submit` | 분석 결과 제출·채점·승급 |

프론트에서는 `/api/backend` 프록시를 경유하면 됩니다.

예시:

```http
/api/backend/api/users/me/title-exams
```

---

## 1.2 사용자 진행 순서

1. 마이페이지에서 현재 칭호와 `next.eligible`을 조회합니다.
2. 응시 가능하면 ‘시험 보기’를 활성화합니다.
3. 시험 생성 API에서 `id`, `practiceContentId`를 받습니다.
4. 아래 요청으로 시험에 연결된 연습 세션을 생성합니다.

```http
POST /api/training-sessions
Content-Type: application/json
Authorization: Bearer <accessToken>
```

```json
{
  "contentId": 123,
  "titleExamId": 456,
  "courseStepId": null,
  "learningFocus": "PRONUNCIATION"
}
```

필드 매핑은 아래와 같습니다.

| 필드 | 값 |
| --- | --- |
| `contentId` | 시험 생성 응답의 `practiceContentId` |
| `titleExamId` | 시험 생성 응답의 `id` |
| `courseStepId` | `null` |
| `learningFocus` | `PRONUNCIATION` |

5. 기존 녹음 업로드·최종 녹음 선택·분석 절차를 진행합니다.
6. 분석 완료 후 해당 `analysisId`를 시험 채점 API에 제출합니다.
7. 합격·불합격 결과를 표시합니다.
8. 마이페이지의 칭호 정보를 다시 조회합니다.

---

## 1.3 요청·응답 계약

### 현재 칭호 조회

```http
GET /api/users/me/title
Authorization: Bearer <accessToken>
```

응답 `data` 필드:

```ts
{
  code: string;
  label: string;
  completedTrainingCount: number;
  minimumTrainingCount: number;
  next: {
    code: string;
    label: string;
    requiredTrainingCount: number;
    remainingTrainingCount: number;
    passingScore: number;
    eligible: boolean;
  } | null;
  updatedAt: string;
}
```

- `code`, `label`: 현재 칭호 코드·이름
- `completedTrainingCount`: 누적 완료 세션 수
- `minimumTrainingCount`: 현재 칭호의 기준 횟수
- `next`: 다음 칭호 정보
- 최고 단계라면 `next`는 `null`입니다.

---

### 시험 생성

```http
POST /api/users/me/title-exams
Authorization: Bearer <accessToken>
Idempotency-Key: optional-string
```

- 요청 본문 없음
- 선택 헤더: `Idempotency-Key`
- 생성 응답 HTTP `201 Created`

응답 `data` 필드:

```ts
{
  id: number;
  currentTitle: string;
  targetTitle: string;
  practiceContentId: number;
  requiredTrainingCount: number;
  passingScore: number;
  status: "READY" | "IN_PROGRESS" | "PASSED" | "FAILED";
  trainingSessionId: number | null;
  createdAt: string;
}
```

참고:

- 생성 API 응답은 생성 시점 기준으로 `status: READY`, `trainingSessionId: null`이 내려갈 수 있습니다.
- 진행 중 시험의 최신 상태가 필요하면 시험 상태 조회 API를 다시 호출하면 됩니다.

---

### 시험 상태 조회

```http
GET /api/users/me/title-exams/{examId}
Authorization: Bearer <accessToken>
```

응답 `data` 필드는 시험 생성 응답과 동일합니다.

```ts
{
  id: number;
  currentTitle: string;
  targetTitle: string;
  practiceContentId: number;
  requiredTrainingCount: number;
  passingScore: number;
  status: "READY" | "IN_PROGRESS" | "PASSED" | "FAILED";
  trainingSessionId: number | null;
  createdAt: string;
}
```

---

### 채점 제출

```http
POST /api/users/me/title-exams/{examId}/submit
Content-Type: application/json
Authorization: Bearer <accessToken>
```

요청 body:

```json
{
  "analysisId": 789
}
```

응답 `data` 필드:

```ts
{
  examId: number;
  status: "PASSED" | "FAILED";
  score: number;
  passingScore: number;
  passed: boolean;
  previousTitle: string;
  currentTitle: string;
  evaluatedAt: string;
}
```

공통 응답 형식은 아래와 같습니다.

```ts
{
  result: boolean;
  message: string;
  data: object | null;
  code?: string;
}
```

---

## 1.4 승급 정책

서버 기본 정책은 아래와 같습니다.

| 승급 목표 | 누적 완료 연습 | 합격 점수 |
| --- | ---: | ---: |
| 초보 | 5회 | 70점 |
| 동네 아나운서 | 15회 | 75점 |
| 아나운서 지망생 | 30회 | 80점 |
| 아나운서 | 60회 | 85점 |

- 시작 칭호는 `왕초보`입니다.
- 횟수 충족은 응시 자격이며 자동 승급 조건이 아닙니다.
- 서버에 저장된 분석의 `overallScore >= passingScore`이면 합격합니다.
- 합격 시 한 단계만 승급합니다.
- 불합격이면 기존 칭호를 유지합니다.
- 시험 생성 시 콘텐츠와 합격 기준을 고정합니다.

---

## 1.5 서버 검증

백엔드에서는 아래 내용을 검증합니다.

- 로그인 사용자 소유의 시험·세션·분석인지 확인
- 분석이 해당 시험의 콘텐츠와 세션에 연결되어 있는지 확인
- 분석 완료 여부 확인
- 선택된 녹음 여부 확인
- 삭제된 녹음이 아닌지 확인
- 유효한 점수가 있는지 확인
- 클라이언트가 보낸 점수가 아닌 서버 저장 점수로 채점
- 같은 분석 재제출은 동일 결과 반환
- 이미 채점된 시험에 다른 분석을 제출하면 거부
- 중복 시험 생성·동시 제출로 중복 승급되지 않도록 트랜잭션 처리

---

## 1.6 주요 오류

| HTTP | 오류 코드 | 의미 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 요청 본문 또는 입력값 오류 |
| 404 | `RESOURCE_NOT_FOUND` | 대상 없음 또는 다른 사용자 소유 |
| 409 | `TITLE_EXAM_NOT_ELIGIBLE` | 응시 횟수 부족 |
| 409 | `MAX_TITLE_REACHED` | 최고 칭호 |
| 409 | `TITLE_EXAM_CONTENT_MISMATCH` | 시험 콘텐츠 불일치 |
| 409 | `ANALYSIS_NOT_COMPLETED` | 채점 가능한 분석이 없음 |
| 409 | `ANALYSIS_SCORE_UNAVAILABLE` | 분석 점수 제공 불가 |
| 409 | `TITLE_EXAM_ALREADY_GRADED` | 이미 채점된 시험 |
| 503 | `TITLE_EXAM_CONTENT_UNAVAILABLE` | 시험 콘텐츠 이용 불가 |

---

## 1.7 운영 확인 필요 사항

아래 내용은 운영 배포 후 확인이 필요합니다.

- 운영 DB의 단계별 정책과 공개된 시험 콘텐츠가 준비되어 있는지
- `title_policies.practice_content_id`가 실제 게시 콘텐츠를 가리키는지
- 승급 시험 완료 세션도 누적 연습 횟수에 포함할지
  - 현재 조회 로직은 모든 `COMPLETED` 세션을 집계합니다.
- 불합격 후 재응시 횟수·대기 시간 정책
- 기존 시험을 생성 API로 다시 요청할 때 상태 반환 방식
  - 현재 생성 응답은 `READY`, `trainingSessionId: null` 기준입니다.
  - 최신 상태는 `GET /api/users/me/title-exams/{examId}`로 조회하면 됩니다.
- 운영 환경에서 생성 → 세션 연결 → 녹음 → 분석 → 채점 → 칭호 갱신까지 통합 테스트

---

# 2. 예시문제 콘텐츠 추가

## 2.1 작업 개요

승급 시험 API와 별개로, 프론트에서 난이도별 예시문제를 조회할 수 있도록 백엔드 DB에 공개 예시문제 콘텐츠를 추가했습니다.

예시문제 콘텐츠는 신규 API가 아니라 기존 학습 콘텐츠 조회 API를 사용합니다.

---

## 2.2 조회 API

```http
GET /api/practice-contents
Authorization: Bearer <accessToken>
```

프론트에서는 `/api/backend` 프록시를 경유합니다.

```http
GET /api/backend/api/practice-contents
```

예시문제 조회 시 아래 query parameter를 사용하면 됩니다.

| Query Parameter | 값 |
| --- | --- |
| `type` | `SENTENCE` |
| `category` | `EXAMPLE_QUESTION` |
| `difficulty` | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` |
| `focus` | `PRONUNCIATION` |
| `page` | `0` |
| `size` | 필요 개수 |

---

## 2.3 난이도별 조회 예시

### 초급 예시문제 조회

```http
GET /api/backend/api/practice-contents?type=SENTENCE&category=EXAMPLE_QUESTION&difficulty=BEGINNER&focus=PRONUNCIATION&page=0&size=10
```

### 중급 예시문제 조회

```http
GET /api/backend/api/practice-contents?type=SENTENCE&category=EXAMPLE_QUESTION&difficulty=INTERMEDIATE&focus=PRONUNCIATION&page=0&size=10
```

### 고급 예시문제 조회

```http
GET /api/backend/api/practice-contents?type=SENTENCE&category=EXAMPLE_QUESTION&difficulty=ADVANCED&focus=PRONUNCIATION&page=0&size=10
```

---

## 2.4 응답 형식

기존 `/api/practice-contents` 응답 형식을 그대로 사용합니다.

```json
{
  "result": true,
  "message": "학습 콘텐츠 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "id": 123,
        "contentType": "SENTENCE",
        "title": "초급 예시문제 1 - 맑은 발음",
        "category": "EXAMPLE_QUESTION",
        "difficulty": "BEGINNER",
        "estimatedSeconds": 8,
        "publisher": null,
        "paragraphCount": null,
        "sentenceCount": null,
        "syllableCount": 24,
        "publishedAt": "2026-10-01T00:00:00Z",
        "speakerName": null
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false
  }
}
```

프론트에서는 `data.items`를 문제 목록으로 사용하면 됩니다.

---

## 2.5 상세 조회

목록에서 받은 `id`로 기존 학습 콘텐츠 상세 조회 API를 사용할 수 있습니다.

```http
GET /api/practice-contents/{contentId}
Authorization: Bearer <accessToken>
```

프론트 프록시 기준:

```http
GET /api/backend/api/practice-contents/{contentId}
```

상세 조회에서는 실제 문장인 `scriptText`를 확인할 수 있습니다.

예상 응답 구조:

```json
{
  "result": true,
  "message": "학습 콘텐츠를 조회했습니다.",
  "data": {
    "id": 123,
    "contentType": "SENTENCE",
    "learningFocus": "PRONUNCIATION",
    "category": "EXAMPLE_QUESTION",
    "title": "초급 예시문제 1 - 맑은 발음",
    "description": "짧은 문장을 또박또박 읽으며 기본 발음과 호흡을 확인합니다.",
    "scriptText": "맑은 하늘 아래에서 아이들이 천천히 걸어갑니다.",
    "difficulty": "BEGINNER",
    "targetPronunciations": [
      "맑은",
      "하늘",
      "천천히"
    ],
    "estimatedSeconds": 8,
    "hasReferenceAudio": false
  }
}
```

---

## 2.6 추가된 콘텐츠 구조

추가된 예시문제는 `practice_contents`에 저장됩니다.

| 필드 | 값 |
| --- | --- |
| `contentType` | `SENTENCE` |
| `category` | `EXAMPLE_QUESTION` |
| `learningFocus` | `PRONUNCIATION` |
| `status` | `PUBLISHED` |
| `ownerId` | `NULL` |

난이도별로 5개씩, 총 15개가 추가됩니다.

| 난이도 | 개수 |
| --- | ---: |
| `BEGINNER` | 5 |
| `INTERMEDIATE` | 5 |
| `ADVANCED` | 5 |

---

## 2.7 추가된 예시문제 제목

### BEGINNER

- 초급 예시문제 1 - 맑은 발음
- 초급 예시문제 2 - 받침 소리
- 초급 예시문제 3 - 또렷한 모음
- 초급 예시문제 4 - 기본 속도
- 초급 예시문제 5 - 짧은 문장

### INTERMEDIATE

- 중급 예시문제 1 - 문장 연결
- 중급 예시문제 2 - 된소리 구분
- 중급 예시문제 3 - 긴 호흡
- 중급 예시문제 4 - 조사 연결
- 중급 예시문제 5 - 리듬 유지

### ADVANCED

- 고급 예시문제 1 - 복합 문장
- 고급 예시문제 2 - 추상어 발음
- 고급 예시문제 3 - 정보 전달
- 고급 예시문제 4 - 긴장 완화
- 고급 예시문제 5 - 발표 문장

---

## 2.8 예시문제로 학습 세션 생성

예시문제 목록에서 선택한 콘텐츠의 `id`를 `contentId`로 사용하면 됩니다.

```http
POST /api/training-sessions
Content-Type: application/json
Authorization: Bearer <accessToken>
```

프론트 프록시 기준:

```http
POST /api/backend/api/training-sessions
```

```json
{
  "contentId": 123,
  "courseStepId": null,
  "learningFocus": "PRONUNCIATION"
}
```

---

# 3. 백엔드 반영 내용

## 3.1 승급 시험 API

- 승급 시험 API 4개 엔드포인트 정합성 보강
- public API 응답 DTO를 `controller/dto`로 분리
- 승급 시험 요청·응답 필드와 문서 명세 정합성 보강
- 에러 응답 메시지와 `code` 응답 정리
- API 문서 갱신
  - `docs/api/title-exam-api.md`
  - `docs/api/specification.md`
  - `docs/api/endpoints.md`

커밋:

```text
5440717 [FEAT]: 칭호 승급 시험 API 계약 정합성 보강
```

---

## 3.2 예시문제 콘텐츠

- Flyway migration 추가
  - `V32__seed_example_question_contents.sql`
- 예시문제 카테고리 추가
  - `SENTENCE / EXAMPLE_QUESTION / 예시문제`
- 예시문제 콘텐츠 추가
  - 난이도별 5개
  - 총 15개
- 관련 문서 추가
  - `docs/api/example-question-content-seed.md`
- PostgreSQL migration 테스트 보강
  - 난이도별 5개씩 들어가는지 검증

커밋:

```text
b410076 [FEAT]: 난이도별 예시문제 콘텐츠 seed 추가
```

---

# 4. 배포 후 확인 필요

## 4.1 승급 시험

운영 DB에서 `title_policies.practice_content_id`가 실제 게시 콘텐츠를 가리키도록 세팅되어 있어야 합니다.

이 값이 없거나 게시 콘텐츠가 아니면 시험 생성 시 아래 오류가 발생할 수 있습니다.

```json
{
  "result": false,
  "message": "승급 시험 요청을 처리할 수 없습니다.",
  "data": null,
  "code": "TITLE_EXAM_CONTENT_UNAVAILABLE"
}
```

---

## 4.2 예시문제 콘텐츠

배포 후 운영 DB에 migration이 적용되면 아래 조건으로 조회 가능합니다.

```sql
select difficulty, count(*)
from practice_contents
where owner_id is null
  and content_type = 'SENTENCE'
  and learning_focus = 'PRONUNCIATION'
  and category = 'EXAMPLE_QUESTION'
  and status = 'PUBLISHED'
group by difficulty;
```

정상 결과:

```text
BEGINNER       5
INTERMEDIATE   5
ADVANCED       5
```

---

# 5. 프론트에서 우선 확인할 것

1. `/api/backend/api/users/me/title` 호출로 칭호 정보가 내려오는지 확인
2. `next.eligible === true`일 때 시험 보기 버튼 활성화
3. `/api/backend/api/users/me/title-exams`로 시험 생성
4. 시험 생성 응답의 `practiceContentId`, `id`로 학습 세션 생성
5. 녹음·분석 완료 후 `analysisId`를 submit API에 제출
6. 예시문제는 아래 API로 난이도별 조회

```http
GET /api/backend/api/practice-contents?type=SENTENCE&category=EXAMPLE_QUESTION&difficulty=BEGINNER&focus=PRONUNCIATION&page=0&size=10
```
