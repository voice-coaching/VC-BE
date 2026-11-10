# 관리자 클래스·문장 관리

일반 콘텐츠 목록의 `CLASS_PRACTICE` 문장을 클래스처럼 취급하지 않는다. `courses → 활성 PRACTICE 단계 → 최신 practice_example_sets → practice_examples` 관계를 기준으로 클래스 한 건과 그 안의 문장을 함께 관리한다.

## API

모두 현재 활성 ADMIN 인증이 필요하다. 일반 회원 403, 비로그인 401, 없는 클래스 404. 성공은 기존 `{result,message,data}` envelope와 `Cache-Control: no-store`를 사용한다.

| Method | Path | 요청 | 응답 |
| --- | --- | --- | --- |
| GET | `/api/admin/courses` | keyword(선택, 제목 부분 검색), page=0, size=20(최대100) | items, page, size, totalElements, totalPages, hasNext |
| GET | `/api/admin/courses/{id}` | 양수 클래스 ID | 클래스 상세 |
| POST | `/api/admin/courses` | course, reason | 201 클래스 상세 |
| PATCH | `/api/admin/courses/{id}` | version, course, reason | 200 새 개정의 클래스 상세 |

상세: `id, version, courseType, title, description, difficulty, status, estimatedMinutes, updatedAt, sentences`.

`version`은 이 클래스 활성 연습 단계의 최신 예문 개정 번호다. 아직 예문 세트가 없으면 0이다. 설정 변경은 새 예문 개정을 만들므로 클래스 정보만 바꾸어도 증가한다.

```json
{
  "version": 1,
  "course": {
    "courseType": "INTONATION",
    "title": "억양 고급 클래스",
    "description": "문장의 의미를 살려 읽습니다.",
    "difficulty": "ADVANCED",
    "status": "PUBLISHED",
    "estimatedMinutes": 5,
    "sentences": [
      {
        "contentId": 260,
        "contentVersion": 0,
        "title": "핵심 가치 강조",
        "text": "핵심 내용을 강조해 전달합니다.",
        "hint": "중요한 단어를 강조해 주세요.",
        "focus": "INTONATION",
        "estimatedSeconds": 15
      }
    ]
  },
  "reason": "클래스 문장 구성 변경"
}
```

- courseType: PRONUNCIATION/INTONATION. difficulty: BEGINNER/INTERMEDIATE/ADVANCED. status: DRAFT/PUBLISHED/HIDDEN.
- title 1~255자, description 최대1000자, reason 필수 1~500자, estimatedMinutes 1~3600 정수.
- sentences 배열 순서가 새 문장 순서다. 1~10개를 저장한다. 기존 5문장에서 하나를 삭제해 4문장으로 저장할 수 있다. 마지막 문장까지 제거한 빈 클래스는 저장하지 않는다.
- 문장 title 필수 최대255자, text 필수 최대10000자, hint 선택 최대500자, focus PRONUNCIATION/INTONATION/BOTH, estimatedSeconds 1~3600 정수.
- 기존 문장은 응답의 contentId/contentVersion을 함께 보낸다. 다른 클래스의 문장 번호, 중복 문장 번호, 변경된 콘텐츠 버전은 거절한다. 새 문장은 두 값을 null로 보낸다. POST는 모두 null이어야 한다.
- 삭제할 문장은 새 배열에서 제외한다. 개별 콘텐츠 삭제 API를 호출하지 않는다. 새 설정은 배열 전체를 하나의 트랜잭션으로 반영한다.
- 400 VALIDATION_ERROR: 입력/문장 수/사유/중복 번호 오류. 409 ADMIN_EDIT_CONFLICT: 오래된 version, 다른 클래스 문장, 변경된 contentVersion, 단일 연습 단계로 결정할 수 없음. 저장소 오류는 기존 503 ADMIN_STORE_UNAVAILABLE.
- 충돌/응답 유실에서 자동 재전송하지 않는다. 입력을 보관하고 명시적으로 최신 상세를 조회해 반영 여부를 확인한다.

## 과거 학습과 TTS

기존 세트·예문·콘텐츠를 수정/삭제하지 않고 새 세트와 새 콘텐츠 ID를 만든다. 같은 원문의 문장도 새 개정에 전용 콘텐츠 ID를 부여해 신규 세션의 예문 조회가 과거 세트를 선택하지 않게 한다. 기존 카테고리·소개·발음 목표는 유지한다. 바뀐 원문은 새 콘텐츠의 기존 TTS 조회/생성 흐름을 사용한다. AI 분석 요청/결과 스키마는 바꾸지 않는다.

진행 중인 세션은 기존 예문 세트 ID를 유지한다. 새 학습은 최신 세트를 사용한다. 클래스 행을 먼저 잠그고 revision을 검사하므로 같은 개정의 동시 수정은 하나만 저장한다. 성공 후 기존 콘텐츠 캐시와 클래스 목록/상세/단계 캐시를 커밋 이후 무효화한다.

스키마 변경·신규 Flyway는 없다. 현재 main에 적용된 V61 이후 스키마와 기존 테이블/열을 사용한다. 과거 V45의 게시 개수 5~10 제한이 현재 스키마에서도 강제된다고 가정하지 않는다.

## 일반 콘텐츠 목록

기존 `GET /api/admin/contents`의 선택 boolean `excludeClassPractice=false`를 사용한다. true이면 CLASS_PRACTICE와 실제 PracticeExample → PracticeExampleSet.stepId → CourseStep 연결이 있는 다른 유형의 문장도 **서버 페이징 전에** 제외한다. 과거 개정의 문장도 제외하며 독립 문장은 유지한다. 제목이나 분류로 연결 관계를 추측하지 않는다. 다른 필터와 교집합으로 적용하며 totalElements/totalPages도 같은 조건을 사용한다. 기본 false이므로 기존 호출은 유지한다.

프론트 `/admin/contents`는 위 클래스 목록을 상단에 표시하고 일반 목록은 `excludeClassPractice=true`로 조회한다. `/admin/courses/{id}` 안에서 클래스와 연결 문장을 함께 편집한다.

## 검증

`AdminCourseIntegrationTest`: ADMIN 접근/no-store, 실제 HTTP 생성·수정·409, 5→4문장 변경, 기존/진행 중 학습의 원문 보존, 신규 학습의 새 원문, 다른 클래스/중복 문장 차단, 동시 수정 한 건만 반영, 일반 목록의 클래스 문장 제외.

로컬 H2와 MockMvc 검증이며 운영 DB/S3/AI 호출을 수행하지 않는다. 운영 배포 시 백엔드 관리 API를 먼저 배포한 뒤 프론트 관리자에서 클래스를 조회한다. 기존 데이터를 자동 수정하는 배포 작업은 없다.
