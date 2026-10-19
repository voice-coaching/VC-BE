# 학습 콘텐츠 및 클래스 문제 확장

- 적용 migration: `V44__expand_practice_content_catalog.sql`
- 작성일: 2026-10-19
- 목적: 뉴스·문장·아나운서 목록의 빈 화면을 줄이고, 클래스별 교정 문제를 5개로 확장한다.

## 범위

| contentType | category | 추가 수 | 적용 결과 |
| --- | --- | ---: | --- |
| `NEWS` | `SOCIETY` | 10 | 학습용 창작 사회 뉴스 10개 추가 |
| `NEWS` | `ECONOMY` | 10 | 학습용 창작 경제 뉴스 10개 추가 |
| `SENTENCE` | `EXAMPLE_QUESTION` | 15 | 기존 문장과 합쳐 난이도별 10개 |
| `ANNOUNCER` | `ANNOUNCER_PRACTICE` | 15 | 난이도별 5개 |
| `CLASS_PRACTICE` | `INTONATION_CLASS` | 12 | 기존 문제와 합쳐 클래스별 5개 |

총 62개의 공개 연습 콘텐츠를 추가한다. 모든 원고는 이번 작업에서 작성한 학습용 창작 원고이며, 외부 뉴스나 타 서비스 원문을 복제하지 않는다.

## 교정 세션 해석

현재 데이터 모델에서 `training_sessions.content_id`는 `practice_contents` 한 건을 참조한다. 따라서 하나의 분석 세션 안에 문제 5~10개를 묶는 구조는 아니다.

이번 요구사항은 API 계약을 변경하지 않고 다음 기준으로 반영한다.

- 일반 목록은 유형·카테고리·난이도별로 반복 연습할 콘텐츠를 충분히 제공한다.
- `억양 기초 클래스`, `억양 흐름 클래스`, `발표 억양 클래스`는 각각 `PRACTICE` 단계 5개를 제공한다.
- 사용자는 각 문제마다 독립된 학습 세션과 분석 결과를 생성한다.

향후 하나의 세션에서 여러 문제를 연속 진행해야 한다면 세션-문제 관계, 문제별 녹음과 분석 상태, 완료 기준을 별도 API·DB 계약으로 설계해야 한다.

## 클래스 구성

V37에서 생성한 각 억양 클래스는 V44 적용 후 다음 구조를 가진다.

| stepOrder | stepType | 설명 |
| ---: | --- | --- |
| 1 | `THEORY` | 억양 개념 학습 |
| 2~6 | `PRACTICE` | 난이도에 맞는 교정 문제 5개 |

새 단계마다 `course_step_revisions` revision 1을 생성한다. 기존 단계와 revision은 수정하거나 삭제하지 않는다.

## 게시 및 TTS

- 공개 콘텐츠이므로 `owner_id`는 `NULL`이다.
- 모든 콘텐츠는 `PUBLISHED` 상태이며 migration 적용 시각을 게시 시각으로 사용한다.
- 뉴스의 `publisher`는 실제 외부 언론사가 아니라 `speakAi 학습 콘텐츠`로 표시한다.
- `V31`의 trigger가 신규 공개 원고를 `content_tts_outbox`에 등록한다.
- 콘텐츠 제목, 유형, 카테고리 조합을 확인해 기존 데이터와 중복 삽입하지 않는다.

## 검증

`ContentCatalogPostgresMigrationTest`에서 fresh PostgreSQL에 V44까지 적용하고 다음을 확인한다.

- `SENTENCE / EXAMPLE_QUESTION`이 난이도별 10개
- 창작 뉴스가 `SOCIETY`, `ECONOMY` 각각 10개
- `ANNOUNCER / ANNOUNCER_PRACTICE`가 난이도별 5개
- 세 억양 클래스의 revision이 연결된 `PRACTICE` 단계가 각각 5개

기존 사용자 세션, 녹음, 분석 결과, 승급시험 정책은 변경하지 않는다.
