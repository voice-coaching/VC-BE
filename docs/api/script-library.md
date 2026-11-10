# 내 원고 보관함 API

2026-11-01: VC-BE V57 마이그레이션과 인증된 원고·주제 API에 연결한다.
프론트엔드에서는 `/api/backend` 프록시를 앞에 붙인다.

| 메서드 | 경로 | 동작 |
| --- | --- | --- |
| GET | `/api/users/me/script-library` | `{ items, topics, assignments }` 조회 |
| POST | `/api/users/me/script-library` | 원고 저장 |
| PUT | `/api/users/me/script-library/{id}` | 새 콘텐츠 버전으로 수정 |
| POST | `/api/users/me/script-library/delete` | `{ ids: number[] }` 일괄 삭제 |
| POST | `/api/users/me/script-library/topics` | `{ name }` 주제 생성 |
| PUT | `/api/users/me/script-library/topics/{id}` | `{ name }` 주제 이름 변경 |
| DELETE | `/api/users/me/script-library/topics/{id}` | 주제와 해당 보관함 항목 삭제 |

원고 저장·수정은 `{ title, scriptText, topicId }`와 `Idempotency-Key`를 전달한다.
성공 시 기존 `PracticeContent` DTO를 반환하므로 상세 조회·녹음 세션 생성에 동일한 ID를 사용한다.
`assignments`는 콘텐츠 ID 문자열에서 주제 이름으로의 매핑이며, 주제가 없으면 `미분류`다.
제목은 100자, 원고는 300자, 주제는 10자 이내이며 Unicode code point 기준이다.
보관함은 계정당 500개, 주제는 30개까지 저장한다. 새 계정의 주제 목록은 비어 있으며 직접 추가할 수 있다.

수정 응답의 ID는 변경된다. 이전 ID는 보관함에서 빠지며 기존 녹음·분석 기록은 원래 원문을 유지한다.
네트워크 단절 후 동일 요청·키를 재전송하면 동일한 저장 결과를 받는다.
이미 삭제되거나 다른 버전으로 교체된 결과의 키를 재사용해 원문을 복구하지 않는다.

보관함·주제 삭제는 기존 학습 기록을 삭제하지 않는다. 학습 기록에서 참조하지 않는 원문은 암호문을 지운다.
마지막 학습 기록을 삭제하더라도 보관함에 저장된 원고는 유지한다.
탈퇴 시 원고, 주제, 보관함 항목과 요청 키를 정리한다.
기존 V22 암호화 설정을 재사용하며, 주제 이름도 암호화하고 모든 읽기·쓰기에 소유권을 검사한다.
조회 응답은 `Cache-Control: no-store`이며 개인 원고를 브라우저 영구 캐시에 저장하지 않는다.

보관함 목록은 인증 principal의 사용자 ID로 `ScriptLibraryEntry.userId`와
`PracticeContent.ownerId`를 모두 제한하고, 삭제된 개인 원고는 제외한다.
다른 사용자의 원고·제목·분류는 반환하지 않으며, 본인 저장 항목이 없으면
`items`와 `topics`는 빈 배열, `assignments`는 빈 객체다(본인 주제가 있으면 주제는 유지).
API 경로·응답 필드·DB 스키마·AI 계약 변경은 없다.

V57은 기존의 삭제되지 않은 개인 원고를 미분류 항목으로 보관함에 등록한다.
공개 콘텐츠와 삭제된 개인 원고는 등록하지 않는다. 기존 암호문은 복사하거나 평문으로 변환하지 않는다.
