# 예시문제 콘텐츠 Seed

`V32__seed_example_question_contents.sql`에서 공개 예시문제 콘텐츠를 추가한다.

## 추가 범위

| contentType | category | learningFocus | difficulty | count |
| --- | --- | --- | --- | ---: |
| `SENTENCE` | `EXAMPLE_QUESTION` | `PRONUNCIATION` | `BEGINNER` | 5 |
| `SENTENCE` | `EXAMPLE_QUESTION` | `PRONUNCIATION` | `INTERMEDIATE` | 5 |
| `SENTENCE` | `EXAMPLE_QUESTION` | `PRONUNCIATION` | `ADVANCED` | 5 |

총 15개 콘텐츠를 `PUBLISHED` 상태로 추가한다.

## 조회 예시

```http
GET /api/practice-contents?type=SENTENCE&category=EXAMPLE_QUESTION&difficulty=BEGINNER&focus=PRONUNCIATION&page=0&size=10
Authorization: Bearer <accessToken>
```

## 운영 참고

- 공개 콘텐츠이므로 `owner_id`는 `NULL`이다.
- `status`는 `PUBLISHED`이며 `published_at`은 migration 적용 시각이다.
- `V31`의 TTS outbox trigger에 따라 공개 콘텐츠 TTS 생성 대상이 될 수 있다.
- 중복 적용 방지를 위해 같은 `content_type`, `category`, `title`의 공개 콘텐츠가 이미 있으면 다시 넣지 않는다.
