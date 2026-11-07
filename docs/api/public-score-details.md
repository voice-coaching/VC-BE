# 선택형 공개 음성 세부점수 전달

2026-11-08 사용자 제공 `frontend-score-details-response.md`에 따른 Backend 구현 후보다.
AI 응답 writer·Frontend 연동 및 운영 배포 완료를 뜻하지 않는다.

기존 GET `/api/v3/analyses/{analysisId}?waitSeconds=8`의
`body.data.canonicalAnalysis.score`에 선택 `details`를 보존한다.
overallScore/validity/reason, 인증·소유권·HTTP 상태·no-store·Backend 정수 ID는 유지한다.
기존 `score.criteria`의 9개 음소 채점표를 새 `details.criteria`로 덮거나 재사용하지 않는다.

## DTO와 수신

`PublicScoreDetails`는 AI가 계산한 표시용 DTO다. 필드가 없는 과거 결과는 details를 생략한다.

| 필드 | 타입·의미 |
| --- | --- |
| schemaVersion | `voice-coaching.score-details.v1` |
| validationStatus | string. 현재 EXPERT_REVIEW_PENDING |
| aggregationMode | AUDIO_ONLY 또는 AUDIO_VISUAL |
| audioScore / visualScore | 0~100 number 또는 null |
| audioWeight / visualWeight | 정수 %, 응답 그대로 보존 |
| criteria | 6개 항목. 아래 공개 필드만 DTO로 전달 |

항목 필드는 criterionId/label/status/reasonCode 문자열, score 숫자 또는 null, weight 정수 %다.
현재 순서는 pronunciation_accuracy, speaking_rate, articulation_rate, pauses, pitch, intensity다.
SCORED의 score는 숫자, UNSCORABLE의 score는 null이다. 숫자 0은 유효하다.
가중치 합·현재 40/10/10/10/15/15·60/40·검산표 revision·CORE/모델 릴리스는 수신 gate로 사용하지 않는다.
원본 CORE·내부 파일 hash·경로·얼굴/음성 좌표를 새 공개 DTO에 추가하지 않는다.

v4/v5/v6 결과 스키마에서 계산 완료·점수 없는 각 score 분기에 details를 선택 허용한다.
추가 하위 필드는 수신에서 허용하고 공개 DTO는 알려진 표시 필드만 전달한다.
필드가 제공됐을 때의 구조·숫자 범위·status/null 관계는 확인하지만 총점과의 산술 대조나 재채점은 하지 않는다.

## 저장·투영 경로

- legacy v6/handoff v2: 기존 원본 결과 bytes 저장 → CanonicalPublicProjection의 Score DTO → 기존 공개 canonicalAnalysis.
- 독립 경로: 원본 GET bytes/SHA 보존 → IndependentResultDocument의 typed details 변환 → 같은 공개 Score DTO.
- Direct 저장 이력: DirectHistoryService의 기존 result_json 원본 보존/조회 경로가 details와 practiceResult를 그대로 유지한다.
  Backend가 Direct writer나 익명 AI 조회/SSE 계약을 새로 구현하지 않는다.

신규 column이나 migration은 필요하지 않다. 기존 원본 bytes/JSON 저장소를 사용한다.
과거 JSON에 없는 details를 소급 생성하지 않고 GPT·채점·분석을 다시 실행하지 않는다.
총점 null에도 일부 유효 세부항목은 그대로 전달한다. Frontend는 전체 총점을 재합산하지 않는다.
기존 입술 visual/subScores 계약과 총평은 변경하지 않는다.

## 적용·검증 범위

Backend 전달 → AI 공개 writer 제공 → Frontend parser·표시 순서로 적용한다.
세부 필드가 없는 배포 조합에서도 기존 총점·총평 계약은 유지한다.
Java 21 compileJava/compileTestJava/bootJar와 패키지 계약·정적 diff로 확인한다.
자동 회귀 테스트·새 fixture·실제 분석/GPT·운영 DB 쓰기·배포는 수행하지 않는다.
AI writer의 새 필드 제공 및 실제 수신→저장→Frontend 표시의 종단 확인은 별도다.
