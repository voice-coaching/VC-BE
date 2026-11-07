# AI V2 handoff / 통합 음성 V6 계약

2026-11-07 수신 호환성 수정: [AI 소유 CORE 수신 계약](ai-owned-core-compatibility.md)을 우선 적용한다.
Backend는 CORE release enum을 수신 gate로 사용하지 않으며 visual coaching 입력은 운영 AI e0b2c5d에 맞춘다.
아래 과거 verifier 설치·검증 기록은 현재 운영 복구나 새 검증 수행의 증거가 아니다.

2026-11-01 기준, 2026-11-02 bounded H5 pin 갱신. 신규 요청 계약을 AI 저장소 `4fc844a35c4a748b1688006b6bf170d0d01f22da`의
`docs/canonical-integration/v6-unification.md` 및 원본 JSON Schema와 맞춘다.
이 문서는 코드 계약이며 운영 전환 완료를 의미하지 않는다.

결과 V6 schema는 이후 [bounded H5 분류 보정](bounded-h5-result-contract-20261102.md)을
포함한다. 아래 pin과 패키징 검증 테스트도 해당 개정의 정확한 바이트 해시를 사용한다.
초기 AI 기준 커밋의 V6 해시와 구분하며, AI·독립 검증기에도 같은 개정을 적용해야 한다.

## 신규 실행과 과거 기록

| 항목 | 신규 음성·영상 실행 |
|---|---|
| Request | `voice-coaching.runpod-analysis-request.v4` |
| Result | `voice-coaching.runpod-analysis-result.v6` |
| Profile | `CANONICAL_AUDIOVISUAL_20261007_V6` — 이름과 달리 음성도 포함 |
| Handoff | `voice-coaching.canonical-handoff.v2` |
| Public view | `voice-coaching.canonical-analysis-view.v3` |

음성은 `video=null`이며, 서버에서 생성한 실제 준비 영수증이 없으면
`mediaPreparation=null`을 명시한다. 실제 영수증이 있는 음성은 그 원본과 digest를
유지한다. 영상은 같은 원본의 실제 준비 영수증과 추가 영상 readiness가 필요하다.
과거 요청을 새 schema로 다시 직렬화하거나 기존 payload·digest·증거를 수정하지 않는다.
재시도는 현재 계약의 새 실행으로 만든다. 과거 결과의 조회 경로는 유지한다.

V6 음성 결과의 `visual.status`는 `NOT_PROVIDED`, score는 null, scoreKind는 `NONE`,
correctiveClaimsAllowed는 false이다. 영상 분석이나 점수를 만들어 채우지 않는다.
수신 원본·해시·실행/녹음 바인딩과 독립 semantic 검증을 통과한 결과만 공개한다.

## 기존 API에서 바뀌는 부분

새 공개 API는 추가하지 않는다.

- `POST /api/training-sessions/{sessionId}/analyze`: schema 선택 헤더가 없거나
  `X-Analysis-Result-Schema: voice-coaching.runpod-analysis-result.v6`이면 V6 실행을 선택한다.
  명시적인 구버전 V5 또는 다른 값은 400 `INVALID_INPUT_VALUE`이다.
- `POST /api/training-sessions/{sessionId}/analysis/retry`: 현재 V6 계약으로 새 실행을 생성한다.
- 기존 capabilities 조회는 신규 제출 지원 결과 schema를 V6으로 알린다.
- `GET /api/v3/analyses/{analysisId}`: V6의 public view는
  `voice-coaching.canonical-analysis-view.v3` schemaVersion을 사용한다.
  기존 응답 envelope와 인증은 유지한다. 과거 V5 결과의 V2 public view도 같은 경로에서
  기존 profile과 schemaVersion을 유지한다. 퇴역한 `/api/v2/analyses`를 신규 조회에 사용하지 않는다.
- 승급시험은 V6 음성 결과도 채점에 사용한다. 본인 세션·선택된 녹음·콘텐츠·완료 상태와
  검증된 committed proof를 요구하며, 가변 `overall_score` 컬럼으로 우회하지 않는다.
  점수가 없거나 증거 검증에 실패하면 승급 점수로 처리하지 않는다.

음성 V6 접수는 공통 readiness로 판단한다. 선택적인 영상 자산이 없다는 이유로 음성까지
차단하지 않는다. 영상은 별도 readiness를 추가로 요구한다. 저장공간/예약 headroom,
오프라인 검증기 설치, worker 실행기와 동일 schema pin은 공통 필수 조건이다.
readiness가 닫혀 있으면 기존 503 `ANALYSIS_INTEGRATION_UNAVAILABLE`을 유지한다.

## 프론트와 함께 전환할 계약

2026-11-01 확인한 VC-FE 기본 브랜치의 음성 클라이언트는 아직 V5 요청 헤더를 보내고,
capabilities의 `analysisProfile`도 V5 리터럴만 허용한다. 이 상태로 백엔드만 전환하면
명시적 V5 요청은 400이 되고 V6 capabilities도 프론트 검증을 통과하지 못한다.

- `src/lib/api/canonical.ts`: 음성·영상의 analyze/retry 모두
  `X-Analysis-Result-Schema: voice-coaching.runpod-analysis-result.v6`을 사용한다.
  이 헤더는 결과 계약 선택이며 실제 영상 입력이 있다는 뜻이 아니다.
- `src/lib/api/analysis-capabilities.ts`: 현재 profile
  `CANONICAL_AUDIOVISUAL_20261007_V6`와 V6 result schema를 확인한다.
  `scopes.TITLE_EXAM.supported`와 `admissionEnabled` 검사도 유지한다.
- 결과는 `/api/v3/analyses/{analysisId}`에서 조회하고 V6 public view V3 및
  음성의 `visual.status=NOT_PROVIDED`를 허용한다. 과거 V5 결과 읽기는 보존한다.
- 음성·영상 헤더, V6 capabilities, 접수 비활성 거부, V6 음성 결과와 과거 결과 조회,
  시험 세션 연결·분석 완료 후 submit을 프론트 회귀 검증에 포함한다.

이는 백엔드의 최초 503을 프론트만 바꿔 해결한다는 뜻이 아니다. 백엔드·독립 검증기·AI의
계약 정합성을 복구하고, 같은 계약을 사용하는 프론트를 함께 배포해야 한다.

## 공통 readiness와 원본 schema pin

AI `/health/handoff`와 백엔드 내부 `/api/internal/ai/worker-readiness/handoff`는 모두 handoff V2 및
아래 **11개 파일 전체의 정확히 같은 SHA-256 map**을 사용한다. 파일 추가·누락·해시 불일치는
접수를 열지 않는다. 오프라인 검증기 `--check-installation`에도 같은 검증을 적용한다.
schema pin은 저장소 원본 바이트 SHA-256이고, 실행 payload digest는 별도 JCS SHA-256이다.

| 파일 | SHA-256 |
|---|---|
| runpod_http_control_v1.schema.json | `58b497491b949b4b672caec4febbc4b4f0950b41a460eebb4f3f31f5092fb0b9` |
| runpod_http_control_v1_2.schema.json | `7acd63e1a0ff9aa68bd2d86c2c5ec37827df10b85b186efac08c1d338287e7a4` |
| runpod_canonical_parent.schema.json | `ab349885e19cec853ba03570dd4bf0d14e0f5130552eea4d907e3b61226af211` |
| runpod_canonical_input.schema.json | `afb262429ecd84625ae2a81692f99219cea7e811aeba7b8914f4dac789835433` |
| runpod_canonical_output.schema.json | `5ca5421f8053b51cc9da9dafe06c0dffcd98e85ca24ce24d373e2d2b0351def8` |
| runpod_canonical_journal_v1.schema.json | `798aa2eee85f9196d0519763119f266b14f065899c03faa75599fab5fed2e5f5` |
| runpod_analysis_request_v4.schema.json | `8fde9cf8ee44b5b151bf31205a355c80789fedc6c8d74ba994274bc898c9eec0` |
| runpod_result_v6.schema.json | `09334a54c3b81c402a7b26136db5826e71fff0a0605338351e92327383033c6a` |
| runpod_canonical_handoff_v2.schema.json | `8677401f5edf88258ca72344cde932b0ddbefdd8e35ccc1885970e6ac2632c07` |
| runpod_media_preparation_v1.schema.json | `0a9c91e88d37f2108a77772512db1d23fcdce48b3a3741407a7f27b31c06946f` |
| runpod_visual_coaching_input_v1.schema.json | `63c560fdd1e775cdce21831ad840e63419f12e53ca5e70023d302bd95cdcdbde` |

원본 schema의 상대 `$id`는 Java validator 로딩용 복사본에서만 URN으로 정규화한다.
패키지 원본 bytes·digest 및 검증 제약은 변경하지 않는다.

수락된 음성 handoff는 실제 `CORE`, `BRIDGE_RESULT`, `BINDING`, `ASSOCIATION` 원본을 요구한다.
`MEDIA_RECEIPT`와 `VISUAL_EVIDENCE`는 실제 바인딩이 있는 경우 요구한다. 코어 실행 전 실패에는
원본 산출물이 없다. 이 경우 빈 artifacts를 유지하며 가짜 증거를 생성하지 않는다.
기존 독립 검증·journal·증거 저장·멱등 commit 경계를 유지한다.

## 운영 전환 순서와 제한

1. 신규 접수를 닫은 상태에서 대기 분석·미완료 handoff·journal을 확인하고 AI 담당자와
   과거 실행의 drain/reconcile 방침을 정한다. 기존 outbox나 immutable 실행을 변환하지 않는다.
2. 동일 AI source revision의 V2 verifier dependency closure, Python 3.12 및 고정 의존성,
   frozen core/LLM 자산을 **별도 release 디렉터리**에 준비한다. AI의
   `deploy/runpod/stage_backend_verifier_bundle.py`는 staging용이며 그 자체로 활성화하지 않는다.
3. 새 bundle의 `--check-installation`에서 READY, handoff V2, 11개 pin 및 frozen 자산 identity를
   확인한다. 현재 V1 검증기 설정을 단순히 V2 문자열로 바꾸거나 검증을 생략하지 않는다.
4. 검토된 새 verifier 경로와 백엔드 코드를 함께 전환하고 양쪽 readiness 및 저장공간을 점검한다.
   위 프론트 요청·capabilities 계약도 함께 전환한다. 기존 실행이 처리 중이면 전환 전
   reconciliation을 완료한다.
5. 신규 QA 음성 요청 → 실제 AI 처리 → handoff 독립 검증/commit → 본인 결과 조회 → 시험 채점까지
   확인한 뒤 접수를 연다. 선택적 영상 활성화는 실제 영상 자산·영수증 검증 후 별도로 한다.

이번 변경은 DB migration이나 운영 레코드 backfill을 추가하지 않는다.
2026-11-01 읽기 전용 점검에서 worker는 현재 11개 pin과 일치했지만 운영 verifier는 V1이었다.
따라서 코드 병합만으로 운영 분석 복구가 완료되는 것은 아니다. 이번 개발 검증은 운영 서비스
재시작·설정 전환·실제 모델 추론을 수행하지 않는다.

## 검증 범위

- 원본 schema pin, 음성 요청의 명시적 null/불변 digest round-trip, 구버전 신규 writer 거부,
  별도 영상 gate, nullable 음성 handoff·공개 projection 및 위조 바인딩 거부를 자동 테스트한다.
- PostgreSQL에서 실제 execution/recording/media snapshot 바인딩과 V6 시험 점수의 committed proof
  경로를 검증한다. `overall_score=99`가 있어도 신뢰된 proof가 없으면 채점하지 않는다.
- 동일 Linux/Python 환경의 임시 디렉터리에서 새 AI verifier source closure를 실행해
  `--check-installation` READY/V2/11개 pin 일치, 합성 pre-core 실패 결과 VALID 및 오디오 해시
  위조 시 INVALID/EVIDENCE_INVALID를 확인했다. 임시 디렉터리는 삭제했다.
- 합성 입력 검증은 실제 모델 추론·사용자 음성 성공 분석·운영 승급시험 종단 확인을 대체하지 않는다.

## Lip provisional consumer (2026-11-07)

Only visual/lipSubScore definitions from AI c312ca7 are adopted. Result v6, overall score and existing binding definitions remain unchanged. See [integration contract](video-lip-provisional-consumer.md).
