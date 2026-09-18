# 근거 기반 코칭 API 추가

2026-09-18, `feat/evidence-based-coaching`. 운영 미반영.

현재 사용자 endpoint와 비동기 polling은 유지한다. `GET /api/analyses/{analysisId}`의 `data.coaching`에 저장된 새 코칭 문서를 추가한다. 본인 소유·COMPLETED 조건은 동일하다. 구형 분석은 `coaching:null`이다. 새 분석 완료 코칭은 저장 문서에서 변환하며 LLM 원본 응답을 직접 전달하지 않는다.

| 필드 | 타입 / null | 의미 |
| --- | --- | --- |
| schemaVersion | string | `voice-coaching.coaching-result.v1` |
| status | string | READY / LIMITED_EVIDENCE / NO_ACTIONABLE_ISSUE |
| summary | string | 현재 확인 가능한 범위와 연습 안내 |
| strengths | array | 현재 빈 배열. 검증되지 않은 강점은 생성하지 않음 |
| items | object[], 최대 3 | 근거가 있는 모델 후보별 관측·설명·행동·연습·자기 확인 |
| practicePlan | string 또는 null | 우선 연습. 항목이 없으면 null |
| limitations | string[] | 실제 판단 제한 |
| comparison | null | 아직 이전 녹음 비교 자료를 생성하지 않음 |
| coverage | object | 기대 표지 수, 보류 수, 포함/제외 후보, 정렬 검사 상태 |
| score | object | validity, overallScore:null, reasonCodes |
| visual | object | 같은 시도 후보별 관측, decode 시각, 판단 단계. 교정 승인=false |
| generation | object | LLM/TEMPLATE, provider, prompt/policy 버전, digest, fallback 분류 |

`items`의 candidateId/evidenceIds, expectedPhone/observedCandidate, 위치·시각·출처·observation은 서버가 확인한 관측이다. explanation/action/practice/selfCheck는 LLM 또는 연결된 기본 연습 문구다. 검출 결과는 확정된 실제 대치/조음 원인이 아니다.
`location`의 word/syllable/charStart/charEnd/writtenRole/startMs/endMs는 null 가능. 글자 위치는 철자 기준이고 시각은 CTC nonblank 구간이다. 영상 관측은 영상 시간축이며 음성 시간축과 무조건 같다고 해석하지 않는다.
문서 내부 필드는 모두 존재한다. 정확한 타입·길이·enum은 [코칭 Schema](../contracts/coaching_result_v1.schema.json)를 따른다.

## 워커 callback

기존 result v1/v2의 필드 허용 범위를 유지하며 `voice-coaching.runpod-analysis-result.v3`를 추가했다.
v3는 overallScore, scoringEvidence, coaching 키가 필수다. 앞의 두 키는 null.
COMPLETED는 유효한 coaching이 필수, FAILED는 coaching:null이다. legacy outcome/발음근거는 호환 출처 정보이며 새 코칭의 확정 교정 대상을 뜻하지 않는다. 프론트는 지원되는 coaching이 있으면 그것을 사용한다.
`COMPLETED_NO_ISSUE`의 legacy summaryFeedback=null 규칙도 유지된다. 새 코칭 안내는 `coaching.summary/items`에서 읽는다.

외부 상태/lease/owner/deadline/eventId 규칙은 동일하다. 전체 코칭을 canonical digest에 포함한다. 동일 내용 재전달은 DUPLICATE, 같은 eventId의 코칭 변경은 충돌이다. 참조 ID·구간·문서 상태의 상호 관계를 검사한 뒤 기존 완료 트랜잭션 안에서 저장한다.

## 점수와 승급

새 코칭은 유효성이 미확정인 총점을 null로 저장한다. 0점으로 치환하지 않는다. 구형 결과의 점수를 변경하거나 다시 채점하지 않는다.
같은 본인 시험 세션의 완료 코칭에 점수가 없으면 submit은 409 `ANALYSIS_SCORE_UNAVAILABLE`을 반환하며 시험 합격/탈락·승급을 적용하지 않는다. 다른 사용자/세션의 분석 접근 조건은 유지된다.

## 저장·전환

V29는 analysis_results에 nullable JSONB `coaching_document`와 버전 CHECK를 추가한다. 기존 row backfill/삭제는 없다. API 계약 검증은 애플리케이션이 담당하고 DB CHECK는 객체와 버전만 확인한다.
BE reader를 먼저 배포하고 검증한 뒤 worker의 명시적 v3 옵션을 켠다. rollback 시 새 발행을 끄되 DB column과 reader는 유지한다. 이전 jar의 JSONB 데이터 호환성과 신규 발행 중지 여부를 확인하지 않고 되돌리지 않는다.

검증 범위: 로컬 callback·중복·소유권·길이·참조 검사와 전체 Java 테스트/빌드. 실제 PostgreSQL V29 적용, 운영 callback, 실제 LLM 품질을 이 테스트 통과로 대체하지 않는다.

구현 근거: `AnalysisRunPodCallbackService.ingestResult`, `AnalysisCoaching.validate`, `AnalysisResult.applyCoaching`, `AnalysisResultReaderImpl.toData`, `AnalysisCoachingResponseDto.from`, `TitleService.submit`.
