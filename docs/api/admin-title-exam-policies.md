# 관리자 승급 시험 설정 API

## 범위와 권한

목표 칭호별 문제 1개와 합격 점수를 관리한다. 문장 등록·수정·공개는 기존 `/api/admin/contents` 계약을 사용한다.
무작위 출제·복수 문장 합산·수동 승급·응시 횟수 정책 변경은 포함하지 않는다.
모든 요청은 `Authorization: Bearer <accessToken>`이 필요하며 관리자 모듈의 서명·활성 로그인 세션·현재 DB의 활성 ADMIN 검증을 적용한다.
이용등급 `membershipType=admin`만으로 접근할 수 없다. 응답은 `Cache-Control: no-store`다.

## GET /api/admin/title-exam-policies

요청 본문·쿼리 없음. 200 응답의 `data.items`는 칭호 순서대로 반환한다.

```json
{
  "result": true,
  "message": "승급 시험 설정을 조회했습니다.",
  "data": {
    "items": [
      {
        "targetRank": "BEGINNER",
        "targetTitle": "초보",
        "version": 0,
        "passingScore": 70,
        "requiredTrainingCount": 5,
        "practiceContentId": 123,
        "available": true,
        "content": {
          "id": 123, "version": 2, "type": "SENTENCE", "focus": "PRONUNCIATION",
          "category": "TITLE_EXAM", "difficulty": "BEGINNER", "title": "초보 승급 시험",
          "description": null, "scriptText": "정확한 발음으로 문장을 읽습니다.",
          "targetPronunciations": [], "estimatedSeconds": 10, "status": "PUBLISHED",
          "publishedAt": "2026-11-07T00:00:00Z", "updatedAt": "2026-11-07T00:00:00Z"
        }
      }
    ]
  }
}
```

`content`는 [관리자 콘텐츠 계약](admin-phase-one.md)의 ContentDto 객체 또는 null이다.

| 필드 | 타입·필수·null | 의미 |
| --- | --- | --- |
| items | array, 필수, non-null | DB에 등록된 승급 대상 정책. 시작 칭호는 제외 |
| targetRank | string, 필수, non-null | BEGINNER / LOCAL_ANNOUNCER / ASPIRING_ANNOUNCER / ANNOUNCER |
| targetTitle | string, 필수, non-null | 초보 / 동네 아나운서 / 아나운서 지망생 / 아나운서 |
| version | integer(int64), 필수, non-null | 설정 수정 버전. 최초 0 |
| passingScore | integer, 필수, non-null | 현재 설정된 합격 점수. 수정은 1~100 |
| requiredTrainingCount | integer(int64), 필수, non-null | 기존 정책의 필요 횟수 값. 이번 API로 변경하지 않으며 응시 자격 판정 변경을 의미하지 않음 |
| practiceContentId | integer(int64), 필수, nullable | 현재 연결된 콘텐츠 ID |
| available | boolean, 필수, non-null | 공개된 공용 SENTENCE / TITLE_EXAM 문장이고 원문이 존재하는지 |
| content | object, 필수, nullable | 기존 ContentDto. 미연결·삭제·비공용이면 null. 미공개 콘텐츠는 객체가 있어도 available=false |

ContentDto의 필드는 `id`, `version`, `type`, `focus`, `category`, `difficulty`, `title`, `description`, `scriptText`,
`targetPronunciations`, `estimatedSeconds`, `status`, `publishedAt`, `updatedAt`이며 기존 타입·null 허용 규칙을 따른다.
프론트는 `content.version`을 별도로 보관하고 저장에 전달한다.

## PATCH /api/admin/title-exam-policies/{targetRank}

대상은 위 네 칭호 중 하나다. 시작 칭호 ABSOLUTE_BEGINNER는 400이다. 정책 행이 없으면 404다.

```json
{
  "practiceContentId": 123,
  "contentVersion": 2,
  "passingScore": 75,
  "version": 0,
  "reason": "검토한 새 시험 문장으로 교체"
}
```

| 필드 | 타입·필수·null | 검증 |
| --- | --- | --- |
| practiceContentId | integer(int64), 필수, non-null | 양수. 삭제되지 않은 공용·공개 SENTENCE / TITLE_EXAM 문장 |
| contentVersion | integer(int64), 필수, non-null | 0 이상. 미리보기한 콘텐츠 버전과 현재 버전 일치 |
| passingScore | integer, 필수, non-null | 1~100 |
| version | integer(int64), 필수, non-null | 0 이상. 조회한 설정 버전과 현재 버전 일치 |
| reason | string, 필수, non-null | 공백 제외 필수, 최대 500자. 개인정보·원문을 쓰지 않음 |

200의 `data`는 수정 후 정책 객체 한 개다. 버전은 1 증가하고 `requiredTrainingCount`는 유지한다.
설정 행 및 콘텐츠 잠금 아래에서 상태·두 버전을 확인하고 `TITLE_EXAM_POLICY_EDIT` 감사 이력을 같은 트랜잭션에 저장한다.
감사 기록의 대상 유형은 TITLE_EXAM_POLICY, 대상 ID는 칭호 코드다. 실패하면 정책·버전도 롤백한다.

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 필수 필드 누락·점수/버전 형식 오류·시작 칭호·부적합 콘텐츠 |
| 401 | UNAUTHORIZED | 토큰 없음·서명 오류·폐기된 로그인 세션 |
| 403 | ACCESS_DENIED | 현재 활성 관리자 아님 |
| 404 | RESOURCE_NOT_FOUND | 정책 또는 관리 가능한 콘텐츠 없음 |
| 409 | ADMIN_EDIT_CONFLICT | 설정 버전 또는 문장 버전 변경, 동시 수정 충돌 |
| 503 | ADMIN_STORE_UNAVAILABLE | 저장소·감사 저장 실패 |

## 기존 문제와 시험 보존

설정 변경은 이후 생성하는 시험부터 적용한다. 기존 `title_exams.practice_content_id`와 `passing_score`는 수정하지 않는다.
현재 정책에 연결되어 있거나 READY/IN_PROGRESS 시험이 사용하는 문제는 원문·분류·학습 초점 변경, 숨김, 삭제를 거부한다.
기존 관리자 콘텐츠 변경 API는 이 경우 `409 ADMIN_TITLE_EXAM_CONTENT_IN_USE`를 반환한다.
제목·설명 등 원문과 무관한 메타데이터 수정은 기존 검증을 따른다.

새 문제는 별도 콘텐츠로 등록·공개한 뒤 연결한다. 기존 문제를 정리하려면 해당 문제를 참조하는 정책을 모두 교체하고
진행 중인 시험이 없어야 한다. 기존 완료 시험·녹음·분석은 삭제하지 않는다.

V67을 적용한 백엔드를 먼저 배포하고 프론트 메뉴를 배포한다. 운영 DB에 수동 SQL 실행이나 기준 점수 일괄 변경을 하지 않는다.
