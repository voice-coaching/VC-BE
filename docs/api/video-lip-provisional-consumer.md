# 영상 입술 임시 점수 백엔드 연동

## 근거와 범위

사용자 전달 `backend-handoff.md`(2026-11-06)를 최신 main `f48a6c8`과 대조했다.
첨부 링크인 backend-reader.patch는 전달 폴더와 원격 브랜치에서 찾지 못했으므로 그대로 적용했다고 주장하지 않는다.
AI [c312ca7 공유 계약](https://github.com/voice-coaching/intelligentAI/blob/c312ca78c7b84e6be49a1ee5ddfb0dcf3565aeb1/docs/contracts/lip-provisional-score-v1.md)과
[result v6](https://github.com/voice-coaching/intelligentAI/blob/c312ca78c7b84e6be49a1ee5ddfb0dcf3565aeb1/docs/contracts/runpod_result_v6.schema.json)의 visual 정의를 기준으로 반영했다.

기존 binding·association·sourceIdentity·종합점수 정의는 유지했다. AI 전체 스키마로 덮어쓰거나
릴리스·schema digest 일치 게이트를 추가하지 않는다. 백엔드는 AI 종합점수를 다시 계산하거나 GPT를 호출하지 않는다.

## 공개 결과 확장

기존 GET `/api/v3/analyses/{analysisId}?waitSeconds=...` 및 ApiResponse.data의
`voice-coaching.canonical-analysis-view.v3` 구조를 유지한다.

| 경로 | 타입·null | 규칙 |
| --- | --- | --- |
| canonicalAnalysis.score.overallScore | number 또는 null | AI가 제공한 값 보존. 0은 유효하며 백엔드 합산 없음 |
| canonicalAnalysis.visual.scoreKind | string, non-null | NONE / CALIBRATED / PROVISIONAL |
| visual.subScores | 선택 array, 없으면 생략 | 최대 4개 |
| visual.phoneAssessments[].scoreKind | string, non-null | 기존 NONE / REFERENCE_SIMILARITY / CALIBRATED에 PROVISIONAL 추가 |
| visual.phoneAssessments[].calibrationRevision | string 또는 null | 전문가 보정값을 생성하지 않음 |
| visual.phoneAssessments[].allowedActionIds | array | [] 또는 [COMPARE_REFERENCE] |
| visual.phoneAssessments[].subScores | 선택 array, 없으면 생략 | 최대 4개 |

위 표의 visual 경로는 모두 data.canonicalAnalysis 아래다. 하위 점수는 부모 점수를 설명하며 종합점수에 다시 더하지 않는다.

하위 항목의 `criterionId`, `label`, `score`, `status`, `reasonCode`는 필수다.
criterionId는 lip_aperture/lip_rounding/lip_closure/lip_timing, status는 SCORED/UNSCORABLE/NOT_APPLICABLE이다.
score는 SCORED일 때 0~100 숫자이고 나머지는 null이다. null score는 생략하거나 0으로 바꾸지 않는다.

선택 measurements는 최대 1개다. metricId(string), value(number), referenceMedian(number), referenceScale(양수),
referenceCount(정수 1~32), tolerance(0 이상), unit(PHONE_PHASE/FACE_SCALE_NORMALIZED)을 그대로 전달한다.
원래 없던 measurements/subScores는 공개 JSON에 만들지 않는다.

전체 입술 점수가 null이어도 국소 PROVISIONAL 점수와 COMPARE_REFERENCE가 존재할 수 있다.
이 경우 음성 종합점수·유효한 피드백을 유지한다. correctiveClaimsAllowed=false이며 전문가 검증 점수로 취급하지 않는다.

## 실제 영상 등록과 결속

1. 기존 `/api/analysis-capabilities`의 구체적 MIME·크기·길이·필수 음성 트랙·공개 영상 동의 정책을 확인한다.
2. 실제 영상 파일을 기존 업로드 URL에 PUT한다. MIME 이름만 video로 바꾸거나 audioBlob으로 대체하지 않는다.
3. 기존 녹음 등록 API에 videoProcessingConsentAccepted=true와 videoProcessingConsentPolicyRevision을 전달한다.
4. 백엔드는 회원 소유 세션·키, 업로드 메타데이터, 실제 컨테이너/코덱·음성 트랙을 확인한다.
5. 실제 파일에서 영상과 PCM을 준비하고 source/canonicalVideo/canonicalPcm SHA 및 mediaPreparation을 저장한다.
6. request v4와 현재 execution에 같은 receipt를 연결하고 result v6 mediaBinding을 확인한 뒤 저장한다.

파일·receipt 불일치는 MEDIA_BINDING_INVALID 등 기존 무결성 오류이며 프레임 부족·정렬 모호성·참조 부재와 다르다.
음성 전용 과거 녹음은 기존 receipt가 없을 수 있다. 이때 video=null을 유지하고 receipt를 만들어 채우지 않는다.
동의는 공개 서비스의 기존 필드에서 처리하며 내부 AI 요청에 새 grant·서명·동의 필드를 추가하지 않는다.

## 배포·활성화 조건

- 새 DB 컬럼이나 마이그레이션을 추가하지 않는다. V41/V42의 미디어 receipt·실행 연결 테이블과 V43의 V6 지원 구조를 사용한다.
- analysis.canonical.audiovisual.worker-enabled / admission-enabled, AI_CANONICAL_AUDIOVISUAL_ENABLED 값은 이 작업에서 바꾸지 않는다.
- 실제 VIDEO 요청이 운영 백엔드 정규화→AI→handoff→저장→공개 조회까지 성공한 뒤 운영자가 VIDEO 접수를 연다.
- 로컬 합성 미디어 테스트·로컬 DB 검증은 운영 영상 분석 성공을 의미하지 않는다.
- 프론트의 실제 영상 전송과 reader 변경은 별도 연동 사항이다. 이번 백엔드 PR에 프론트 배포는 포함하지 않는다.

내부 171번 실행의 runpod_execution_failed 원인을 해결했다고 주장하지 않는다.
해당 실패는 AI 실행 로그를 requestId/executionId로 별도 대조해야 한다.
