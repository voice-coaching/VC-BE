# 클래스 문장 순차 연습

## 클래스 진입

`GET /api/courses/{courseId}/practice-examples`

- 인증: ACTIVE 회원의 Bearer JWT.
- path: `courseId`는 양수 정수.
- query: `sessionId`는 선택 양수 정수. 본인 소유이며 같은 클래스의 연습 단계에 속해야 한다.
- request body: 없음.
- response: 200, 기존 `result/message/data` envelope.

```json
{
  "result": true,
  "message": "OK",
  "data": {
    "courseId": 301,
    "stepId": 303,
    "revision": 1,
    "items": [
      {
        "id": "example-1",
        "order": 1,
        "text": "연습할 문장입니다.",
        "hint": null,
        "focus": "PRONUNCIATION",
        "locale": "ko-KR",
        "practiceContentId": 123
      }
    ]
  }
}
```

예시는 항목 하나만 표시했다. 기존 클래스 데이터는 문장 5개를 사용한다. 기존 게시 계약의 5~10개 검증은 유지하며 응답을 임의로 5개로 자르지 않는다. 프론트는 실제 `items.length`를 표시하고 `order` 순으로 연습한다.

| data 필드 | 타입 / null | 의미 |
| --- | --- | --- |
| courseId | integer / 불가 | 클래스 ID |
| stepId | integer / 불가 | 실제 연습 단계 ID, 세션 생성 시 courseStepId에 사용 |
| revision | integer / 불가 | 예문 세트 개정 |
| items | array / 불가 | 순서대로 정렬된 예문 목록 |
| items[].id | string / 불가 | 예문 ID |
| items[].order | integer / 불가 | 1부터 시작하는 문장 순서 |
| items[].text | string / 불가 | 녹음할 문장 |
| items[].hint | string / 허용 | 연습 안내 |
| items[].focus | string / 허용 | PRONUNCIATION, INTONATION, BOTH |
| items[].locale | string / 불가 | ko-KR |
| items[].practiceContentId | integer / 불가 | 세션 생성 시 contentId에 사용 |

클래스의 현재 단일 비은퇴 PRACTICE 단계를 서버가 선택한다. 미게시/없는 클래스는 404, 연습 단계가 없거나 둘 이상이면 503이다. 예문 개정 선택·게시 검증·세션 소유권 검사·현재 진행 중 세션의 개정 고정은 기존 단계별 예문 API와 같다. 선택 sessionId가 다른 단계 또는 타인 소유면 404이다.

| HTTP | code | envelope message / 조건 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 예문 요청을 처리할 수 없습니다. / 양수가 아닌 ID |
| 401 | 기존 인증 오류 | 기존 인증 필터 응답 / 미인증 또는 유효하지 않은 토큰 |
| 403 | FORBIDDEN | 예문 요청을 처리할 수 없습니다. / 비활성 회원 |
| 404 | RESOURCE_NOT_FOUND | 예문 요청을 처리할 수 없습니다. / 클래스·세션을 조회할 수 없음 |
| 503 | PRACTICE_EXAMPLES_UNAVAILABLE | 예문 요청을 처리할 수 없습니다. / 단일 연습 단계 또는 게시 예문이 준비되지 않음 |

## 프론트 변경

클래스 진입 시 위 API를 호출하고 첫 문장부터 표시한다. THEORY 단계 ID나 `lesson` 쿼리 숫자로 예문 요청을 만들지 않는다. 응답 `stepId`와 현재 예문의 `practiceContentId`로 기존 학습 세션을 생성한다. 각 문장의 녹음·분석·완료 후 다음 문장으로 이동한다. 이론 카드, 별도 듣기 단계, 별도 결과 확인 단계를 필수 화면으로 표시하지 않는다. 기준 음성 재생은 문장별 선택 기능으로 기존 API를 사용할 수 있다.

기존 `GET /api/courses/{courseId}/steps/{stepId}/practice-examples`는 유지한다. 은퇴한 THEORY 단계에 이 경로를 호출하면 예문 세트가 없으므로 여전히 503이다. 프론트의 새 진입 API 전환이 필요하며 서버가 다른 단계의 데이터를 잘못된 ID 아래 반환하지 않는다.

## 범위

V53은 기존 문장 5개와 단일 PRACTICE 단계를 가진 클래스의 THEORY/AUDIO_EXAMPLE/RESULT_REVIEW 단계를 은퇴 처리한다. 단계 목록과 상세의 stepCount는 은퇴 단계를 제외한다. 문장 수와 stepCount는 다르다. 정리된 클래스는 연습 단계 하나 안에 문장 5개를 가진다. 기존 stepOrder/ID/개정/세션/예문은 보존한다. 과거 단계 상세의 직접 조회는 계속 지원한다.

이번 작업에는 문장별 진도 계산, 완료율 자동 갱신, 서버의 다음 문장 잠금 기능을 추가하지 않는다. 기존 진도·완료 API의 계산 방식은 유지한다. 은퇴 단계는 필수 완료 대상에서만 제외한다.

배포 시 V53 적용 후 단계 목록을 확인한다. 클래스 상세/단계 캐시는 v2 이름으로 전환하여 이전 이론 단계 목록을 재사용하지 않는다. 이론 보강용 수동 SQL은 새 흐름에 필요하지 않다.
