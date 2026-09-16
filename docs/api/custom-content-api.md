# 사용자 입력 문장

이슈 #61 / 상위 #52. 법적 동의·비밀번호 재설정·결제 구독 제외.

## API

`POST /api/practice-contents/custom`: Bearer 인증, 201 envelope, 선택 Idempotency-Key.
필수 body: title(1~100 code point), scriptText(trim 후 1~300 code point),
learningFocus(PRONUNCIATION/INTONATION/BOTH), retention(SESSION_HISTORY), locale(ko-KR).
제어문자/잘못된 Unicode/다른 locale·retention은 400 VALIDATION_ERROR.
문장 분리는 서버의 한국어 BreakIterator를 사용하며 offset은 원고 내 Unicode code point 기준이다.
estimatedSeconds는 code point 수를 초당 5개로 환산한 안내값이며 최소 1초다. 실제 학습 시간과 별개다.

사용자 확정에 따라 응답 id는 기존 API와 동일한 숫자다. 프론트는 custom_ 문자열을 생성하거나 기대하지 않고 응답 id를 그대로 contentId에 전달해야 한다.
기존 상세 필드에 origin=USER_INPUT, sentences, createdAt을 추가한다. 공개 콘텐츠의 세 필드는 null이다.
생성 이후 `GET /api/practice-contents/{contentId}` 및 `POST /api/training-sessions` 등 기존 흐름을 사용한다.
본인만 조회/사용 가능하며 타인과 삭제된 콘텐츠는 404 RESOURCE_NOT_FOUND다.
custom과 courseStepId/titleExamId 조합은 409 CONFLICT다.

## 멱등성과 보존

같은 사용자/키/정규화된 body는 최초 id와 생성 시간을 반환한다. 다른 body는 409 CONFLICT.
키는 공백 없는 ASCII 1~128자이며 SHA-256으로 저장한다. body 비교값은 keyed HMAC이고 원문을 기록하지 않는다.
사용자 행 잠금과 unique 제약으로 동시 생성/마지막 기록 삭제/탈퇴와의 경쟁을 직렬화한다.
여러 세션이 같은 원고를 참조할 수 있다. 마지막 연결 학습 기록을 삭제하면 암호문과 제목을 제거한다.
삭제된 원고의 이전 키를 재시도하면 404이며, 원고를 복원하지 않는다. 새 작성은 새 키를 사용한다.
탈퇴는 미사용 원고를 포함해 모두 제거한다. 세션/외래키 정합을 위한 원문 없는 tombstone은 남는다.
기록 삭제는 기존 음원 삭제 outbox 및 분석 취소 경로를 재사용한다.

## 저장과 운영 설정

V22는 기존 공개 콘텐츠에 영향 없이 소유자·암호문 필드와 멱등 요청 테이블을 추가한다.
custom은 DB 제약으로 HIDDEN만 허용되므로 공개 목록/추천/필터에 섞이지 않는다.
기존 title/script_text에는 고정 표시값만 저장하고 실제 입력은 AES-256-GCM 암호문에 저장한다.
원고를 포함한 custom 분석 요청 outbox payload도 같은 방식으로 암호화한다. 기존 공개 콘텐츠 outbox는 유지한다.
세션/분석 요청 조회에서는 복호화된 원고를 사용하며 가짜 분석 결과는 생성하지 않는다.

서버 환경 `CUSTOM_CONTENT_ENCRYPTION_KEY`에 Base64 인코딩한 32-byte 키를 설정해야 한다.
키가 없거나 잘못되면 생성/본인 custom 조회·학습 연결은 503 TEMPORARY_UNAVAILABLE이며 평문 저장으로 우회하지 않는다.
키는 저장소나 프론트로 전달하지 않는다. 이 버전은 단일 키이므로 기존 암호문을 이관하지 않고 키를 교체하면 복호화할 수 없다.
운영 키 배포·백업 및 키 회전 절차는 운영에서 관리한다. 이번 PR은 운영 설정을 변경하지 않는다.

복호화된 사용자 제목/원고를 Redis에 복제하지 않도록 기록 목록·상세와 홈 최근 학습의 응답 캐시를 사용하지 않는다.
다른 통계·공개 콘텐츠 캐시는 유지한다. 응답은 기존 인증·소유권 검증을 거친다.
기존 AI 결과/세그먼트 저장, 음원 및 Redis transport의 보호·보존은 기존 인프라 정책을 따르며 이번 필드 암호화만으로 모든 저장소 검증을 대신하지 않는다.

## 검증 범위

Unicode 경계, 위조 소유권, 멱등/동시 요청, DB 암호문, 인증 태그 위조 거절,
학습 입력 원고/기록 원고 일치, 마지막 기록 삭제/탈퇴, PostgreSQL 제약·이관을 자동 검증한다.
실제 AI 서버를 통한 녹음→분석→결과, 운영 암호화 키 설정, 프론트 타입/브라우저 종단 검증은 별도다.
현재 AI 운영 준비 조건과 분석 admission 정책을 그대로 유지한다.
