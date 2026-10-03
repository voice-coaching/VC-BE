# Canonical 전체 음소 채점 연동

2026-10-03 구현. 운영 배포·실제 추론 QA는 이번 작업에서 수행하지 않았다.

RunPod의 새 scored-H5는 자음 일치율 거절 게이트를 제거하고 Seungun 전체 음소 근거로 버전 고정 9항목 점수를 계산한다. 기존 Native/P1 코칭을 유지하며 GPT는 서버 검산표의 숫자를 바꿀 수 없다. Backend는 보관 근거의 오프라인 검산을 통과한 callback만 적용한다.

- `CanonicalCallbackDocument`: 검증 대상 score에서 RUBRIC_COMPUTED 숫자를 읽는다.
- `CanonicalCallbackCommitter`, `CanonicalResultCompletion`, `AnalysisResult`: 검증 후 기존 overall_score 컬럼에 저장한다. 실패·과거 결과에는 null을 유지한다. 새 migration은 없다.
- `CanonicalAnalysisView.Score`: rubricRevision과 criteria를 추가한다. 각 criterionId/level을 그대로 전달하고 null level도 직렬화한다. 과거 null 점수 응답에는 새 필드를 추가하지 않는다.
- `CanonicalSemanticVerifier`: 기존 S7/H5와 새 core/manifest/lock이 모두 설치돼야 readiness를 허용한다.
- `docs/contracts/runpod_result_v4.schema.json`: AI 저장소와 동일한 계약이다.

성공 score는 overallScore(0~100, 소수점 첫째 자리), validity=RUBRIC_COMPUTED, reason=EVIDENCE_BOUND_PRACTICE_SCORE, rubricRevision=phone-rubric-20261003-v1, criteria 9개다. 순서는 vowels/plain_stops/tense_stops/aspirated_stops/fricatives/affricates/nasals/liquid/coverage다. level은 0~4 또는 제외 시 null이다. Backend는 가중치 산술을 중복 구현하지 않는다.

채점 실패는 null/UNSCORABLE/SCORING_FAILED와 FAILED/완료 불가로 제공한다. 비ACCEPT 입력은 null/NOT_AVAILABLE/INPUT_NOT_ACCEPTED, 전달 크기 제한 실패는 null/NOT_AVAILABLE/RESULT_NOT_DELIVERED다. 과거 S7/H5 null 점수는 그대로 조회된다. 기존 기록을 자동 재채점하지 않는다. 칭호 시험의 canonical 제외 정책은 유지한다. 대본·채점 revision 간 점수의 동등성을 주장하지 않는다.

새 core manifest: `6ca67fcdaed390cc3da634fbafaef153890e52b8775c431f3fefae7dc122c776`

새 H5 manifest: `820d600fab4c49615050b9a038e7236f0429f68249dc631a39c0f5ab0be6f0f6`

새 lock: `17d327b5898b183274418996468b577f3c455d8c1a5868b9cf3e00a34d53114d`

배포 시 신규 접수와 진행 중 실행을 확인한 뒤 AI 저장소의 전체 검증 묶음(기존 core/S7/H5 + 새 core/H5)을 AWS에 설치한다. Linux Python3.12의 verifier 설치 검사를 확인하고 Backend를 교체한다. 새 계약 지원 FE 이후 RunPod를 활성화한다. JAR만 교체하면 새 verifier identity가 없어 readiness가 실패한다.

검증: Java21 `gradlew.bat compileJava -x test --no-daemon`, 공유 schema 메타 검증 및 byte 일치. 자동 테스트·실제 음성/GPT/브라우저 QA는 수행하지 않았다. Backend는 develop PR로 인계하며 운영 배포는 별도다.
