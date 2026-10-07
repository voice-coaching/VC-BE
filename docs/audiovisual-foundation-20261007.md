# 음성·영상 v6 구현 인계

2026-10-07. 초기 기반 위에 v6 접수·검증·저장·조회 경로를 구현했다. 운영 배포 및 DB migration은 실행하지 않았다.

## 변경 파일

- `RunPodAnalysisJobRequest`, `RunPodOutboxAnalysisJobPublisher`, `TrainingAnalysisSchemaAdmissionService`: 선택된 영상 recording의 receipt와 request v4/result v6 dispatch. v5 영상 접수는 계속 422.
- `CanonicalExecutionMediaStore`, V41–V43: immutable recording preparation와 execution snapshot, v6 transport 및 7종 handoff artifact 허용. V43은 기존 데이터 변경/backfill 없음.
- `CanonicalDeliverySpool`, `CanonicalDeliveryWorker`, `CanonicalHandoffWorker`: 새 artifact·118,620,160바이트 예약, v6 offline 검증 전 공개 금지, 검증 후 소유/실행 상태 재확인. 기존 v5 조기 공개 경로에 v6를 넣지 않음.
- `CanonicalCallbackCommitter`, `CanonicalCommittedResultReader`, `CanonicalPublicProjection`, `CanonicalAnalysisQueryService`: 저장된 미디어 binding 확인 및 public view v3, 실제 result-contract 반환.
- `CanonicalArchiveWriter`: 최소 영상 근거와 receipt의 기존 B2 archive 연결. RGB/전체 얼굴 좌표를 public API에 제공하지 않음.
- `RecordingDeletionDelivery`: 기존 사용자 삭제 outbox에서 v6 execution deadline까지 PCM/MP4 삭제 유예. 분석 성공을 이유로 사용자 원본을 즉시 삭제하지 않음.
- `CanonicalActionPolicy`: 재시도 가능 여부를 해당 결과의 실제 schema readiness로 판단.
- `CanonicalAudiovisualReadiness` 및 인증 controller: V43, verifier/H5 pin, 공통 schema digest, AI /health/audiovisual, 기존 handoff readiness 및 spool 예산 확인.

## 공개 API

기존 POST `/api/training-sessions/{sessionId}/analyze` 및 retry URL/body 유지. 영상은 `X-Analysis-Result-Schema: voice-coaching.runpod-analysis-result.v6`, 음성은 기존 v5. GET `/api/v3/analyses/{analysisId}`는 저장 profile에 따라 view v2 또는 v3를 반환한다. 영상 상세 최대 128개와 생략 수, null 점수와 coverage, 자음 MFA 구간, 제한된 관찰만 노출한다. 음성 점수와 입술 점수는 합산하지 않는다.

## 기본값 및 미완료 조건

`analysis.canonical.audiovisual.worker-enabled=false`, `analysis.canonical.audiovisual.admission-enabled=false`. verifier는 AI PR #34의 확장 소스 closure와 새 H5가 필요하다. Backend PR #114는 기존 PR #113 변경을 포함한다.

AI 후보 H5: `ea7e573f0212802b2f6d09043873de31aad870403fab4b4618d4fcc8ae80a1e2`; lock: `3de3db047604a68b23427281e724b67fd602e241cca46fce36dc17f84fc6bcfa`.

참조/보정 catalog는 비어 있어 관찰 전용이며 실제 MFA 자음 위치만 지원한다. 지정 영상 추출·전문가 구간 확인·별도 보정 자료, 개발자 실제 QA, migration 검증과 운영 설치/전환이 남았다. 구 v5-only Backend로 데이터가 있는 DB를 되돌리지 않는다.

## 확인

`compileJava --offline --no-daemon`, 공통 JSON schema 바이트/구조 정적 확인. 자동 회귀 테스트·DB migration·실제 미디어/GPT QA·배포는 실행하지 않았다.
