# AI 파이프라인과 백엔드 API 활용 범위 점검

## 범위와 판정

- 점검일: 2026-09-16. 소스와 기존 기록을 읽은 정적 점검이다. 모델 실행, 자동 회귀 테스트, 새 품질 QA, 원격 재배포는 수행하지 않았다.
- Backend: `VC-BE`, `runpod이랑-AWS-연결`, `bb7ae92` (PR #55 작업 코드).
- AI: `intelligentAI`, `agent/runpod-http-v1_1`, `b6c445c`. 미추적 `docs/runpod_a40_video_validation_20260916.md`를 기존 관측 기록으로 참고했으며 수정하지 않았다.
- 양쪽 `runpod_http_control_v1.schema.json`, `runpod_result_v1.schema.json`의 SHA-256이 각각 일치한다.
- **핵심 Seungun → 선택적 입술 분석 → 선택적 Clova 설명 → 결과 저장/조회는 코드로 연결되어 있다. 그러나 AI 저장소의 모든 기능과 진단 정보를 백엔드 API가 활용하는 것은 아니다.**
- 스키마 일치는 실제 운영 배포·저장소 연결·음성 추론 성공을 증명하지 않는다. 이 점검에서는 현재 운영 상태를 다시 조회하지 않았다.

## 운영 호출 경로

`세션/녹음 등록·선택 → analyze → TrainingAnalysisRequestService → RunPod outbox → POST /v1/analysis-jobs → claim → create_http_core → process_admitted → Seungun → optional visual → optional grounded Clova → result_payload → Backend result callback → DB → GET /api/analyses/{id}`

근거:

- [Backend 요청 구성](../src/main/java/org/example/voice/training/application/TrainingAnalysisRequestService.java)
- [AI 운영 구성](../../intelligentAI/src/voice_coach/backend_analysis/production.py)
- [AI 공통 처리](../../intelligentAI/src/voice_coach/backend_analysis/service.py)
- [AI HTTP 입력/결과 변환](../../intelligentAI/src/voice_coach/backend_analysis/http_models.py)

## 기능별 대조

| 기능 | AI 구현 및 현재 연결 | Backend 활용 | 판정 |
| --- | --- | --- | --- |
| 기준 대본·g2pK 기대 음소 | 서버 콘텐츠 대본을 기대 음소로 변환, 운영 구성에서 단어 경계 기호 제거 | contentId/scriptText/scriptSha256/promptRevision 전달 | 연결됨 |
| 녹음 형식 정규화·무결성 | 저장소에서 제한된 객체 읽기, 메타데이터/SHA 확인, 격리된 오디오 decode | 원본을 canonical WAV/MP4로 정규화 후 key/MIME/크기/SHA/길이 전달 | 연결됨 |
| Seungun 발음 탐지 | frozen detector 및 production decision, 한 번의 분석 결과 재사용 | 선택 음소/index/시간/score/threshold/근거 의미 저장·조회 | 핵심 결과 연결됨 |
| 문제 없음 | no_issue를 COMPLETED_NO_ISSUE로 반환, 교정 문장 강제 생성 안 함 | outcome 저장/조회 | 연결됨 |
| 영상·입술 보완 | 같은 시도의 선택 음소와 영상에 결합한 visual evidence 생성 | video 입력 전달, visualSupplement의 승인 ID/rendererKey/anchor/hash 저장·조회 | 핵심 보완 연결됨; 상세 계측은 제한됨 |
| Clova 상세 설명 | AI_ANALYSIS_CLOVA_ENABLED=true일 때 같은 시도 근거로 생성·검증 | 최종 summaryFeedback 문자열 저장·조회 | 조건부 연결됨; 생성 성공 여부는 구분 불가 |
| Clova/영상 실패 시 대체 처리 | 근거가 승인된 문장으로 fallback, 시각 실패가 음성 결과를 지우지 않음 | 최종 문장/음성 결과 수신 | 결과는 보존되지만 이유가 대부분 유실됨 |
| 실행 제어 | claim/heartbeat/상태/취소/결과 재전송 | v1.1 claim/digest/lease/owner/중복 및 timeout/cancel 처리 | 코드 연결됨 |
| 피드백 재생성 | 생성 모듈은 존재하지만 HTTP 작업은 전체 분석 단위, 별도 재생성 경로 없음 | DefaultAiFeedbackProvider가 기존 승인 문장을 반환 | 새 Clova 생성에는 미연결 |
| 상세 진행 상태 | worker에 RUNNING/DELIVERING_RESULT 등의 상태 존재 | DB status 기반 WAITING/PRONUNCIATION_ANALYSIS와 0/70/100 임시값 | 부분 활용 |
| 전체 음소 근거 | production evidence_bundle에 전체 phones와 결정 근거 존재 | HTTP는 선택된 한 음소만 투영, segments는 생성하지 않음 | 의도된 단일 교정 범위; 전체 음소 화면에는 부족 |
| 입술 계측/closed-beta 관측 | 상세 supplement와 closedBetaLipObservation을 생성할 수 있음 | HTTP serializer가 관측 상세를 제외; 공개 DTO 필드는 남아 있음 | 현재 HTTP에서 상세 수치 미전달 |
| S1/S2 교차 검증·참조음 | 별도 PronunciationFeedbackPipeline이 content alignment/GOP S1/Qwen S2/reference bank를 구성 | 운영 create_http_core가 이 파이프라인을 호출하지 않음 | 미연결, 운영 채택 여부는 별도 결정 필요 |
| Oracle/closed-loop 재녹음 결정 | 별도 FastAPI의 /v1/oracle-attempts, /v1/closed-loop/oracle-attempts | 호출 adapter 없음. Backend retry는 실패 작업 재시도임 | 미연결, 운영 음성 분석과 동일 기능으로 취급하면 안 됨 |
| STT·억양·속도·종합 점수 | 현재 운영 HTTP 계약은 해당 결과를 제공하지 않음 | 기존 조회 DTO에 필드는 있으나 null/빈 목록 | 현재 운영에서 제공되지 않는 기능 |
| 학습·평가·데이터 수집·참조음 생성 | training/evaluation/data/references 등 오프라인 도구 존재 | 사용자 분석 API가 직접 실행하지 않음 | 오프라인 기능으로 분리 유지가 적절 |

## 개선이 필요한 지점

### 1. 재생성 API의 동작이 실제 AI 재생성과 다름

`POST /api/analyses/{id}/feedback/regenerate`는 [DefaultAiFeedbackProvider](../src/main/java/org/example/voice/analysis/infrastructure/DefaultAiFeedbackProvider.java)의 `approvedSummaryFeedback`을 그대로 반환한다. [FeedbackRegenerationService](../src/main/java/org/example/voice/analysis/application/FeedbackRegenerationService.java)는 이 경우에도 재생성 횟수를 증가시킨다.

AI 측에는 근거 기반 생성 모듈이 있지만, 현재 API로 저장된 근거를 재사용해 Clova만 다시 호출하는 경로는 없다. 제품이 새로운 설명을 기대한다면 실제 생성 계약과 필요한 승인 근거 보관/전달을 설계해야 한다. 같은 문장 반환 기능을 그대로 재생성으로 안내하지 않는 조치도 필요하다.

### 2. Clova 생성 성공·검증 거절·fallback 구분 정보가 사라짐

[GroundedVideoFeedback](../../intelligentAI/src/voice_coach/pronunciation_feedback/video_grounded_advice.py)는 `generator_status`, `generator_id`, `generator_revision`, `fallback_reason`, `visual_claim_status`를 가진다. 그러나 [BackendGroundedFeedback.render](../../intelligentAI/src/voice_coach/backend_analysis/grounded_feedback.py)는 `feedback.message`만 반환한다. 즉 AI의 Backend adapter에서 이미 정보가 소실되므로 백엔드 DTO만 추가해서는 해결되지 않는다.

영향: 최종 문장이 있어도 Clova가 성공했는지, 모델 미사용/시간 초과/검증 거절로 카탈로그 문장을 반환했는지 Backend API에서 구분할 수 없다. 안전한 생성 상태·버전·정형 reason code를 양측 계약에 추가하는 것이 우선이다. 원문 프롬프트나 모델의 미승인 답변을 공개할 필요는 없다.

기존 미추적 [A40 영상 관측 기록](../../intelligentAI/docs/runpod_a40_video_validation_20260916.md)에는 두 영상 모두 음성/입술 분석 후 Clova 출력이 필수 근거 누락으로 거부되어 `catalog_fallback`을 사용했다고 적혀 있다. 이는 기존 기록이며 이번 점검에서 재현하거나 최신 상태로 재확인한 결과가 아니다.

### 3. 입술 보완의 null 사유와 상세 관측 미전달

[service.py](../../intelligentAI/src/voice_coach/backend_analysis/service.py)의 `_visual_supplement`는 일부 실패를 포착해 빈 supplement로 진행한다. [http_models.py](../../intelligentAI/src/voice_coach/backend_analysis/http_models.py)의 `result_payload`는 스키마 허용 필드만 남긴다.

따라서 `visualSupplement=null`만으로 영상 미제공, 승인할 시각 근거 없음, 분석 실패 등을 구분하기 어렵다. `closedBetaLipObservation`은 Backend 공개 DTO/DB에 있으나 HTTP 결과에서는 제외되므로 이번 경로에서 새로 채워지지 않는다. 최종 설명 문자열에는 승인된 입술 교정이 포함될 수 있으므로 입술 기능 전체가 미사용인 것은 아니다.

우선 시각 처리 상태·정형 실패 사유를 추가하고, 계측치를 제품에서 표시할 필요가 있다면 승인된 최소 projection을 별도 버전으로 합의해야 한다. 내부 debug/media/path를 그대로 노출하지 않는다.

### 4. 진행률·준비 상태 표시가 실제 워커 관측값과 다름

[TrainingAnalysisReaderImpl](../src/main/java/org/example/voice/training/infrastructure/TrainingAnalysisReaderImpl.java)은 PROCESSING을 항상 PRONUNCIATION_ANALYSIS/70으로 표시한다. 실제 입술/Clova/결과 전달 단계와 무관하다. worker 상태 조회 API도 Backend client에서 호출하지 않는다. 상세 단계가 필요하면 heartbeat에 단계를 합의하거나 진단용 상태 조회를 연결해야 하며, 근거 없이 정밀 퍼센트를 만들 필요는 없다.

[AnalysisCapabilitiesReaderImpl](../src/main/java/org/example/voice/analysis/infrastructure/AnalysisCapabilitiesReaderImpl.java)의 CONFIGURED는 설정 존재 여부다. 새 Backend worker-readiness도 Backend DB/계약 준비 여부이며 전체 GPU/S3 상태를 대신하지 않는다. 가용성 표시를 추가할 경우 서로 다른 개념으로 제공해야 한다.

### 5. lease 설정 범위의 잠재적 불일치

AI [http_runtime.py](../../intelligentAI/src/voice_coach/backend_analysis/http_runtime.py)의 `_lease`는 남은 lease를 `0 < duration <= 90`초로 제한한다. Backend `AI_ANALYSIS_CLAIM_TTL`은 양수 확인만 하고 상한은 없다. 기본 90초에서는 맞지만 운영자가 이를 늘리면 스키마가 같아도 팟이 lease를 거절한다.

Backend TTL을 90초 이하로 검증하거나 양측에서 같은 상한 설정을 공유해야 한다. 이는 현재 기본값에서 발생한 장애라는 의미가 아니라, 소스로 확인한 설정 호환성 결함이다.

## 의도된 제외와 실제 미연결의 구분

- Seungun의 coach_one은 여러 후보 중 하나를 선택하는 현재 운영 정책이다. 전체 음소 근거가 없다고 곧바로 탐지 모델을 사용하지 않는 것으로 판단할 수 없다.
- 별도 일반 PronunciationFeedbackPipeline의 S1/S2/reference 경로는 코드가 있지만 production.py가 선택하지 않는다. 이를 붙이면 진단 주체와 자산·시간·GPU 요구사항이 달라지므로 단순 누락 필드 추가로 처리하면 안 된다.
- HTTP에서 grant/동의 증빙/debug를 제외한 것은 기존 사용자 결정이다. Backend 로그인·소유권·동의 기록과 양방향 토큰·파일 무결성 검증은 별도로 유지한다.
- 오프라인 학습·자료 수집·평가·연구 Oracle를 사용자 요청마다 실행할 필요는 없다. 현재 운영 모델이 제공하지 않는 점수나 STT를 임의로 채우는 것도 올바른 연결이 아니다.

## 권장 순서

1. 피드백 재생성의 실제 동작을 제품 의미에 맞추고, Clova/시각 처리 상태를 내부 계약에 보존한다.
2. TTL 상한과 가용성/진행 표시를 실제 지원 범위에 맞춘다.
3. 입술 상세 계측·전체 음소 근거·재녹음 비교가 필요한지 정한 뒤 승인된 응답 구조를 확장한다.
4. 배포 revision과 저장소 설정을 확인한 환경에서 개발자가 실제 음성/영상 처리 및 피드백 품질을 검증한다.

이번 작업은 이 점검 문서만 추가했다. 양쪽 실행 코드, AI의 미추적 관측 문서, 환경 설정은 변경하지 않았으며 커밋·푸시·배포는 하지 않았다.
