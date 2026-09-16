# 칭호 및 승급 시험

이슈 #58 / 상위 #52. 법적 동의·비밀번호 재설정·결제 구독은 제외한다.

## 계약

모두 사용자 Bearer 인증과 기존 응답 envelope를 사용한다.

| Method | Path | 응답 |
| --- | --- | --- |
| GET | /api/users/me/title | 200 현재 칭호/완료 학습 수/다음 시험 자격 |
| POST | /api/users/me/title-exams | 201 현재 다음 단계 시험, 선택 Idempotency-Key |
| GET | /api/users/me/title-exams/{examId} | 200 본인 시험과 연결 세션 상태 |
| POST | /api/users/me/title-exams/{examId}/submit | 200 서버 채점 결과, body={analysisId} |

POST /api/training-sessions에 선택 `titleExamId`를 추가한다. 시험 콘텐츠 ID가 일치해야 하며 courseStepId는 함께 사용하지 않는다. 동일 시험의 유효한 세션이 이미 있으면 재사용한다. 취소/실패/삭제된 세션은 새 세션으로 다시 연결할 수 있다. 일반 세션의 기존 계약은 유지한다.

칭호 코드: ABSOLUTE_BEGINNER(왕초보), BEGINNER(초보), LOCAL_ANNOUNCER(동네 아나운서), ASPIRING_ANNOUNCER(아나운서 지망생), ANNOUNCER(아나운서).

최고 칭호는 next:null이다. 완료 학습 수는 현재 남아 있는 COMPLETED 세션 수이며 결과 조회/재분석은 횟수를 증가시키지 않는다. 학습 횟수만으로 승급하지 않는다.

## 정책과 콘텐츠

V20의 title_policies에 기본 자격 5/15/30/60과 합격점 70/75/80/85를 등록한다. 운영에서 각 목표 칭호에 사용할 실제 게시 콘텐츠의 practice_content_id를 지정해야 한다. 임의 콘텐츠/가짜 분석 점수를 만들지 않는다. 미지정/미게시 콘텐츠는 503 TITLE_EXAM_CONTENT_UNAVAILABLE다.

정책 변경은 이후 신규 시험에만 적용한다. 시험 생성 당시 목표/콘텐츠 ID/자격 횟수/합격점을 고정한다. READY 또는 IN_PROGRESS 시험이 있으면 새 시험을 만들지 않는다. 시험에 지정한 게시 콘텐츠의 원문은 진행 중 임의 수정하지 않아야 한다.

## 채점과 멱등성

- 본인 시험 세션, 지정 콘텐츠, 선택된 미삭제 녹음, 완료된 분석의 overallScore만 사용한다. 재녹음 요구/불확실/실패 결과는 채점하지 않는다.
- 사용자 행 잠금 아래 시험 채점과 한 단계 승급을 한 트랜잭션에서 반영한다. 사용자/목표별 활성 시험, 시험 연결 세션, 채점 분석 ID에 unique 제약을 둔다.
- 같은 분석으로 재제출하면 최초 결과를 반환한다. 다른 분석으로 재채점하면 409 TITLE_EXAM_ALREADY_GRADED다. 과거 시험 결과의 currentTitle은 채점 당시 snapshot이며 현재 실제 칭호는 GET title로 조회한다.
- 생성 Idempotency-Key는 공백 없는 ASCII 1~128자다. 여러 키가 같은 진행 시험을 참조해도 각각 기록해 후속 재요청이 새 시험을 생성하지 않게 한다. 생성 응답은 최초 READY 상태, 생성 시간, 정책 snapshot을 유지한다. 최신 상태는 GET exam으로 조회한다.
- 불합격 후 새로운 키 또는 키 없이 시험을 생성해 재응시할 수 있다.

## 오류

- 400 VALIDATION_ERROR: 잘못된 ID/JSON/멱등 키
- 401 AUTHENTICATION_REQUIRED, 403 FORBIDDEN: 인증 또는 제한 사용자
- 404 RESOURCE_NOT_FOUND: 본인 시험/분석이 아님 또는 미존재
- 409 TITLE_EXAM_NOT_ELIGIBLE, MAX_TITLE_REACHED, TITLE_EXAM_CONTENT_MISMATCH, TITLE_EXAM_ALREADY_GRADED, ANALYSIS_NOT_COMPLETED, CONFLICT
- 503 TEMPORARY_UNAVAILABLE 또는 TITLE_EXAM_CONTENT_UNAVAILABLE: 정책/게시 콘텐츠 미준비

운영 콘텐츠 지정과 실제 음성 분석을 통한 프론트 종단 검증은 별도로 필요하다. 기존 #57 프로필 사진 V19 다음에 V20을 적용한다.

현재 RunPod HTTP v1.1 결과 스키마와 RunPodAnalysisResultCallbackRequestDto는 overallScore를 전달하지 않는다. 콜백 변환에서 점수는 null로 저장되므로 AI 계약 보강 전에는 해당 경로의 분석으로 실제 승급 채점이 불가능하다. 점수 값을 추측하거나 발음 evidence 값을 점수로 임의 변환하지 않는다. 테스트는 서버 DB에 소유권이 확인된 유효 분석 점수가 존재하는 경우의 채점 규칙을 검증한다.
