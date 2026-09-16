# 클래스 단계 교육 내용

이슈 #60 / 상위 #52. 법적 동의·비밀번호 재설정·결제 구독 제외.

## 계약

`GET /api/courses/{courseId}/steps/{stepId}`: Bearer 인증, 200 기존 envelope.
data: id, courseId, stepOrder, stepType, title, subtitle(nullable), contentRevision, blocks, completed.
기존 단계 목록에는 items와 동일한 길이의 stepCount를 추가한다. 시작 직후 0% 진도는 완료로 표시하지 않는다.

- 잘못된 숫자 ID: 400 VALIDATION_ERROR
- 미인증: 401
- 미게시 클래스, 다른 클래스 소속 단계, 타인/다른 단계 sessionId: 404 RESOURCE_NOT_FOUND
- 미등록/빈 교육 내용, 손상된 구조화 데이터: 503 COURSE_CONTENT_UNAVAILABLE
- 단계와 다른 콘텐츠로 학습 생성: 409 COURSE_CONTENT_MISMATCH

## 구조화 blocks

TEXT(title?, body), IMAGE(assetUrl, altText, aspectRatio), DIAGRAM(diagram.kind, diagram.altText),
CHECKLIST(items), AUDIO(referenceAudioId), PRACTICE_PROMPT(practiceContentId).
임의 HTML은 허용하지 않는다. 이미지는 인증 정보·query·fragment 없는 HTTPS URL만 허용한다.
DIAGRAM kind는 현재 명세에서 확인된 TONGUE_POSITION_RIEUL만 지원하며 새 kind는 프론트와 계약 후 추가한다.
프론트는 문자열을 HTML로 해석하지 않고 TEXT로 표시한다.

## 개정 유지

V21은 기존 course_steps의 실제 body와 practice_content_id를 revision 1로 이관한다.
새 예문, 다이어그램 또는 음성을 만들어 채우지 않는다. 기존 내용이 비어 있으면 503이며 운영 콘텐츠 등록이 필요하다.
course_step_revisions는 추가만 가능하다. PostgreSQL trigger가 UPDATE/DELETE를 거절하며 기존 세션이 참조하는 내용을 보존한다.
새 교육 내용을 게시할 때 동일 step_id와 증가한 revision의 새 행에 제목/종류/순서/blocks 전체를 등록한다.
별도 게시 관리 API는 이번 범위에 없다. 이미지/참조 음원/원고의 실제 배포 및 검수는 콘텐츠 운영 담당 영역이다.

학습 세션 생성 시 최신 개정 ID를 저장한다. 기존 세션은 V21 이관 시 revision 1에 연결한다.
기본 상세 조회는 본인의 가장 최근 진행 중 세션에 고정된 개정을 반환하며, 없으면 최신 개정을 반환한다.
여러 세션 또는 과거 학습의 내용을 정확히 지정하려면 선택 query `sessionId`를 사용한다. 본인 소유권을 검사한다.
세션이 완료돼도 보존된 개정은 삭제하지 않는다. 선택 sessionId가 없으면 완료 후 최신 개정이 표시될 수 있다.
API에서 DB의 내부 revision 행 ID는 노출하지 않는다.

실제 클래스 교육 콘텐츠 검수와 프론트 타입 대조/브라우저 종단 검증은 별도다.
