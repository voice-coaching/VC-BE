# 승급시험 문제 및 억양 클래스 작업 완료 공유

백엔드에서 프론트 요청 사항인 아래 2가지를 DB seed로 반영했습니다.

1. 승급시험 문제 추가
2. 억양 클래스 추가

이번 작업은 **새 API 추가가 아니라 기존 API에서 조회될 운영 데이터 추가**입니다.
배포 후 Flyway migration이 적용되면 프론트는 기존 API 흐름을 그대로 사용하면 됩니다.

---

# 1. 승급시험 문제 추가

## 1.1 반영 방식

승급시험 생성 API가 사용할 수 있도록 `practice_contents`에 공개 시험 문장을 추가하고, `title_policies.practice_content_id`를 해당 콘텐츠로 연결했습니다.

Migration:

```text
src/main/resources/db/migration/V37__seed_title_exam_and_intonation_courses.sql
```

## 1.2 추가된 콘텐츠

| 승급 목표 | 연결된 문제 제목 | difficulty | learningFocus |
| --- | --- | --- | --- |
| `BEGINNER` | 승급시험 - 초보 | `BEGINNER` | `PRONUNCIATION` |
| `LOCAL_ANNOUNCER` | 승급시험 - 동네 아나운서 | `INTERMEDIATE` | `PRONUNCIATION` |
| `ASPIRING_ANNOUNCER` | 승급시험 - 아나운서 지망생 | `ADVANCED` | `PRONUNCIATION` |
| `ANNOUNCER` | 승급시험 - 아나운서 | `ADVANCED` | `PRONUNCIATION` |

콘텐츠 분류:

```text
contentType = SENTENCE
category = TITLE_EXAM
status = PUBLISHED
ownerId = null
```

## 1.3 프론트 사용 방식

프론트는 기존 승급시험 생성 API를 그대로 호출하면 됩니다.

```http
POST /api/backend/api/users/me/title-exams
Authorization: Bearer <accessToken>
```

응답의 `practiceContentId`가 이번에 추가한 승급시험 문제를 가리킵니다.

응답 예시:

```json
{
  "result": true,
  "message": "승급 시험을 생성했습니다.",
  "data": {
    "id": 456,
    "currentTitle": "ABSOLUTE_BEGINNER",
    "targetTitle": "BEGINNER",
    "practiceContentId": 123,
    "requiredTrainingCount": 5,
    "passingScore": 70,
    "status": "READY",
    "trainingSessionId": null,
    "createdAt": "2026-10-03T00:00:00Z"
  }
}
```

이후 기존 흐름대로 `practiceContentId`를 `contentId`로 넣어 세션을 생성하면 됩니다.

```http
POST /api/backend/api/training-sessions
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

---

# 2. 억양 클래스 추가

## 2.1 반영 방식

기존 클래스 API에서 조회될 수 있도록 `courses`, `course_steps`, `course_step_revisions`, `practice_contents`에 억양 클래스 데이터를 추가했습니다.

새 API는 없습니다.

## 2.2 추가된 클래스

| 클래스 제목 | courseType | difficulty | stepCount |
| --- | --- | --- | ---: |
| 억양 기초 클래스 | `INTONATION` | `BEGINNER` | 2 |
| 억양 흐름 클래스 | `INTONATION` | `INTERMEDIATE` | 2 |
| 발표 억양 클래스 | `INTONATION` | `ADVANCED` | 2 |

각 클래스는 아래 단계로 구성됩니다.

| stepOrder | stepType | 설명 |
| ---: | --- | --- |
| 1 | `THEORY` | 억양 학습 개념 설명 |
| 2 | `PRACTICE` | 억양 연습 문장 녹음 |

## 2.3 추가된 연습 콘텐츠

| 콘텐츠 제목 | contentType | category | difficulty | learningFocus |
| --- | --- | --- | --- | --- |
| 억양 초급 연습 - 문장 끝 올림과 내림 | `CLASS_PRACTICE` | `INTONATION_CLASS` | `BEGINNER` | `INTONATION` |
| 억양 중급 연습 - 의미 단위 끊어 읽기 | `CLASS_PRACTICE` | `INTONATION_CLASS` | `INTERMEDIATE` | `INTONATION` |
| 억양 고급 연습 - 강조와 설득 | `CLASS_PRACTICE` | `INTONATION_CLASS` | `ADVANCED` | `INTONATION` |

## 2.4 프론트 조회 방식

기존 클래스 목록 API를 그대로 사용하면 됩니다.

```http
GET /api/backend/api/courses?type=INTONATION&status=PUBLISHED&page=0&size=10
Authorization: Bearer <accessToken>
```

난이도별 조회도 가능합니다.

```http
GET /api/backend/api/courses?type=INTONATION&difficulty=BEGINNER&status=PUBLISHED&page=0&size=10
```

```http
GET /api/backend/api/courses?type=INTONATION&difficulty=INTERMEDIATE&status=PUBLISHED&page=0&size=10
```

```http
GET /api/backend/api/courses?type=INTONATION&difficulty=ADVANCED&status=PUBLISHED&page=0&size=10
```

## 2.5 클래스 상세 및 단계 조회

기존 API를 그대로 사용하면 됩니다.

```http
GET /api/backend/api/courses/{courseId}
Authorization: Bearer <accessToken>
```

```http
GET /api/backend/api/courses/{courseId}/steps
Authorization: Bearer <accessToken>
```

```http
GET /api/backend/api/courses/{courseId}/steps/{stepId}/education
Authorization: Bearer <accessToken>
```

`PRACTICE` 단계의 education 응답에는 `PRACTICE_PROMPT` block이 포함되고, 이 block의 `practiceContentId`로 기존 학습 세션을 생성하면 됩니다.

---

# 3. 배포 후 확인 요청

배포 후 운영 DB에 Flyway migration이 적용되면 아래를 확인하면 됩니다.

## 3.1 승급시험 문제 연결 확인

```sql
select policy.target_rank, content.id, content.title, content.category, content.status
from title_policies policy
join practice_contents content on content.id = policy.practice_content_id
order by policy.required_training_count;
```

정상 기준:

- 4개 승급 정책이 모두 조회됨
- `category = TITLE_EXAM`
- `status = PUBLISHED`

## 3.2 억양 클래스 확인

```sql
select course.difficulty, course.title, count(step.id) as step_count
from courses course
join course_steps step on step.course_id = course.id
where course.course_type = 'INTONATION'
  and course.status = 'PUBLISHED'
group by course.id, course.difficulty, course.title
order by course.created_at, course.id;
```

정상 기준:

- 억양 클래스 3개 조회
- 각 클래스 `step_count = 2`

---

# 4. 프론트에서 수정이 필요한지

## 승급시험

별도 수정 필요 없음.

기존 승급시험 생성 응답의 `practiceContentId`를 사용하면 됩니다.

## 억양 클래스

프론트에서 이미 클래스 목록 API에 `type=INTONATION` 필터를 붙여 호출할 수 있다면 별도 수정 필요 없습니다.

만약 현재 발음 클래스만 고정 조회하고 있다면, 억양 탭 또는 억양 목록에서는 아래 query를 사용하도록 연결하면 됩니다.

```http
GET /api/backend/api/courses?type=INTONATION&status=PUBLISHED&page=0&size=10
```

---

# 5. 주의사항

- 이번 작업은 억양 클래스 UI 노출과 학습 흐름을 위한 콘텐츠 seed입니다.
- 현재 AI 분석 계약이 억양 점수를 실제로 산출한다고 보장하는 것은 아닙니다.
- 분석 요청 가능 여부는 기존 `learningFocus`, capability, AI provider 정책을 그대로 따릅니다.
