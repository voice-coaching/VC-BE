# 학습 콘텐츠 및 클래스 문제 확장

- 적용 migration: `V44__expand_practice_content_catalog.sql`, `V46__seed_pronunciation_courses.sql`
- 작성일: 2026-10-19
- 목적: 뉴스·문장·아나운서 목록의 빈 화면을 줄이고, 클래스별 교정 문제를 5개로 확장한다.

## 범위

| contentType | category | 추가 수 | 적용 결과 |
| --- | --- | ---: | --- |
| `NEWS` | `SOCIETY` | 10 | 학습용 창작 사회 뉴스 10개 추가 |
| `NEWS` | `ECONOMY` | 10 | 학습용 창작 경제 뉴스 10개 추가 |
| `SENTENCE` | `EXAMPLE_QUESTION` | 15 | 기존 문장과 합쳐 난이도별 10개 |
| `ANNOUNCER` | `ANNOUNCER_PRACTICE` | 15 | 난이도별 5개 |
| `CLASS_PRACTICE` | `INTONATION_CLASS` | 12 | 억양 클래스별 순차 예문 5개 |
| `CLASS_PRACTICE` | `PRONUNCIATION_CLASS` | 15 | 발음 클래스별 순차 예문 5개 |

총 77개의 공개 연습 콘텐츠를 추가한다. 모든 원고는 이번 작업에서 작성한 학습용 창작 원고이며, 외부 뉴스나 타 서비스 원문을 복제하지 않는다.

## 교정 세션 해석

사용자 화면에서는 클래스의 `PRACTICE` 단계 하나를 한 교정 세션으로 표시하고, 그 안에서 API가 반환한 예문을 순서대로 모두 수행한다. 서버의 기존 녹음·분석 모델은 `training_sessions.content_id` 한 건을 기준으로 하므로 각 예문은 독립된 학습 세션과 분석 결과를 가진다.

- `GET /api/courses/{courseId}/steps/{stepId}/practice-examples`가 `order` 1부터 시작하는 예문 5~10개를 반환한다.
- 프론트는 `order` 순서대로 각 `practiceContentId`에 대해 기존 학습 세션을 생성한다.
- 앞 문제의 학습 세션과 분석을 완료한 뒤 다음 문제로 이동한다.
- 같은 교정 세션 화면의 전체 문제 수와 진행 상태는 `items.length`로 계산한다.
- 클래스 단계 진도와 클래스 완료 처리는 반환된 모든 문제 완료 후에만 수행한다.

별도의 교정 세션 ID나 일괄 생성 API는 추가하지 않는다. 화면상 교정 세션 진행 상태의 중단·재개는 현재 프론트 상태로 관리하며, 서버 영속 재개가 필요해지면 별도 API 계약으로 확장한다.

## 클래스 구성

V37에서 생성한 억양 클래스와 V46에서 생성한 발음 클래스는 다음 구조를 가진다.

| stepOrder | stepType | 설명 |
| ---: | --- | --- |
| 1 | `THEORY` | 발음 또는 억양 개념 학습 |
| 2 | `PRACTICE` | 난이도에 맞는 예문을 순차 수행하는 교정 단계. 현재 seed는 5개 |

기존 `PRACTICE` 단계와 `course_step_revisions` revision 1은 유지한다. 단계별 `practice_example_sets` revision 1과 현재 순서가 1~5인 `practice_examples`를 게시한다. V45 이후 다른 revision은 5~10개를 가질 수 있다.

## 게시 및 TTS

- 공개 콘텐츠이므로 `owner_id`는 `NULL`이다.
- 모든 콘텐츠는 `PUBLISHED` 상태이며 migration 적용 시각을 게시 시각으로 사용한다.
- 뉴스의 `publisher`는 실제 외부 언론사가 아니라 `speakAi 학습 콘텐츠`로 표시한다.
- `V31`의 trigger가 신규 공개 원고를 `content_tts_outbox`에 등록한다.
- 콘텐츠 제목, 유형, 카테고리 조합을 확인해 기존 데이터와 중복 삽입하지 않는다.

## 검증

`ContentCatalogPostgresMigrationTest`에서 fresh PostgreSQL에 V46까지 적용하고 다음을 확인한다.

- `SENTENCE / EXAMPLE_QUESTION`이 난이도별 10개
- 창작 뉴스가 `SOCIETY`, `ECONOMY` 각각 10개
- `ANNOUNCER / ANNOUNCER_PRACTICE`가 난이도별 5개
- 세 억양 클래스와 세 발음 클래스의 `PRACTICE` 단계가 각각 1개
- 각 `PRACTICE` 단계 revision에 연결된 공개 예문이 순서대로 5개

기존 사용자 세션, 녹음, 분석 결과, 승급시험 정책은 변경하지 않는다.
