# 승급시험 문제 및 억양 클래스 Seed

작성일: 2026-10-03

## 목적

프론트에서 요청한 아래 운영 데이터 누락을 Flyway migration으로 보완한다.

- 승급시험 생성 시 사용할 공개 시험 문제
- 클래스 목록에서 조회할 수 있는 억양 클래스

새로운 공개 API를 추가하지 않는다. 기존 API가 조회하는 DB 데이터를 추가한다.

## Migration

```text
src/main/resources/db/migration/V37__seed_title_exam_and_intonation_courses.sql
```

## 승급시험 문제

`practice_contents`에 승급시험용 공개 문장을 추가하고, `title_policies.practice_content_id`를 해당 콘텐츠로 연결한다.

### 추가 카테고리

| contentType | category | label |
| --- | --- | --- |
| `SENTENCE` | `TITLE_EXAM` | 승급시험 |

### 추가 콘텐츠

| targetRank | content title | difficulty | learningFocus |
| --- | --- | --- | --- |
| `BEGINNER` | 승급시험 - 초보 | `BEGINNER` | `PRONUNCIATION` |
| `LOCAL_ANNOUNCER` | 승급시험 - 동네 아나운서 | `INTERMEDIATE` | `PRONUNCIATION` |
| `ASPIRING_ANNOUNCER` | 승급시험 - 아나운서 지망생 | `ADVANCED` | `PRONUNCIATION` |
| `ANNOUNCER` | 승급시험 - 아나운서 | `ADVANCED` | `PRONUNCIATION` |

### 프론트 영향

승급시험 생성 API는 그대로 사용한다.

```http
POST /api/users/me/title-exams
```

응답의 `practiceContentId`가 위 seed 콘텐츠 중 하나를 가리킨다. 프론트는 기존 흐름대로 해당 `practiceContentId`로 학습 세션을 생성하면 된다.

## 억양 클래스

`courses`, `course_steps`, `course_step_revisions`, `practice_contents`에 억양 클래스 3개와 각 클래스의 이론/연습 단계를 추가한다.

### 추가 카테고리

| contentType | category | label |
| --- | --- | --- |
| `CLASS_PRACTICE` | `INTONATION_CLASS` | 억양 클래스 |

### 추가 클래스

| title | courseType | difficulty | stepCount |
| --- | --- | --- | ---: |
| 억양 기초 클래스 | `INTONATION` | `BEGINNER` | 2 |
| 억양 흐름 클래스 | `INTONATION` | `INTERMEDIATE` | 2 |
| 발표 억양 클래스 | `INTONATION` | `ADVANCED` | 2 |

각 클래스는 아래 2단계로 구성된다.

| stepOrder | stepType | description |
| ---: | --- | --- |
| 1 | `THEORY` | 억양 학습 개념 설명 |
| 2 | `PRACTICE` | `CLASS_PRACTICE / INTONATION_CLASS` 콘텐츠 녹음 연습 |

### 프론트 조회

기존 클래스 목록 API를 그대로 사용한다.

```http
GET /api/courses?type=INTONATION&status=PUBLISHED&page=0&size=10
```

클래스 상세와 단계 조회도 기존 API를 사용한다.

```http
GET /api/courses/{courseId}
GET /api/courses/{courseId}/steps
GET /api/courses/{courseId}/steps/{stepId}/education
```

## 배포 후 확인 SQL

### 승급시험 정책 연결 확인

```sql
select policy.target_rank, content.id, content.title, content.category, content.status
from title_policies policy
join practice_contents content on content.id = policy.practice_content_id
order by policy.required_training_count;
```

정상 기준:

- `target_rank` 4개가 모두 조회된다.
- `content.category = 'TITLE_EXAM'`
- `content.status = 'PUBLISHED'`

### 억양 클래스 확인

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

- 억양 클래스 3개가 조회된다.
- 각 클래스의 `step_count = 2`

## 주의사항

- 억양 클래스는 학습 콘텐츠와 클래스 UI 노출을 위한 seed다.
- 현재 AI 분석 계약은 억양 점수를 실제로 산출한다고 보장하지 않는다.
- 분석 요청 가능 여부는 기존 `learningFocus`, capability, 분석 provider 정책을 따른다.
