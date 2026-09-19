# 현재 RunPod 발음 피드백 목록과 연결 확인

관측: 2026-09-18 14:21~14:26 UTC (23:21~23:26 KST). 읽기 전용 운영 조사이며 새 분석·LLM 생성·배포·재시작·DB 변경은 실행하지 않았다.

## 확인 근거와 리비전

- 로컬 VC-BE: `runpod이랑-AWS-연결`, `4d6d2c0c0f57db652cf1a63a426e7ca4f9a9d19c`.
- AWS: alpha-backend/nginx active, Java PID 17918, 작업 디렉터리 `/opt/alpha/app`.
- 실행 인자의 JAR는 `/opt/alpha/app.jar`, 현재 링크 대상은 `/opt/alpha/releases/d936066cba1d70bc152eb309b1c3614c71a5bdd3.jar`.
- 현재 JAR SHA-256: `af65d4b3c3deb77ef4ed9c08bee251328352d29e2fd3761e9b6e84c8788bdab7`. 링크 대상과 메모리에 로드된 모든 클래스의 동일성까지 증명한 것은 아니다.
- RunPod API PID 41664, Seungun runtime PID 41669, CLOVA PID 20312의 실행 모듈을 확인했다. API 8080과 CLOVA 127.0.0.1:8000이 listening 중이다.
- 인증된 `GET /health/services`: HTTP 200, `status=ready`, `executorReady=true`, workerRevision=`0648007cf689dfd16866f46cf636d3ecd6adeae2`.
- RunPod 배포 디렉터리에는 Git 메타데이터가 없다. 주요 10개 파일을 로컬 AI 체크아웃 `C:/Users/Public/Documents/ESTsoft/CreatorTemp/clova-feedback-policy-20260918`과 LF 정규화 SHA-256으로 대조해 일치를 확인했다. 전체 트리의 커밋 일치로 확대하지 않는다.
- 대조 파일: `backend_analysis/{service,http_api,http_runtime,domain,hierarchical_scoring,grounded_feedback}.py`, `backend_analysis/adapters/{seungun,visual}.py`, `pronunciation_feedback/grounded_advice.py`, `pronunciation_feedback/adapters/advice_generator_hyperclova.py` (모두 AI 저장소의 `src/voice_coach/` 하위).
- RunPod 환경파일은 CLOVA 활성화, 전체 근거 전달 활성화, 채점 활성화, `clova-phone-rubric-v3`를 지정한다. `/proc/.../cwd`는 OS 권한으로 읽지 못했다. 환경파일을 실행 프로세스의 전체 메모리 상태로 간주하지 않는다.

## 현재 연결 API

AWS 실행 프로세스 환경에서 `AI_ANALYSIS_TRANSPORT=runpod_http`, `ANALYSIS_STREAM_ENABLED=false`를 확인했다.

| 방향 | API | 역할 |
|---|---|---|
| 사용자 → BE | `POST /api/training-sessions/{id}/analyze` | 분석 요청·동의 검증 후 outbox 기록 |
| BE → RunPod | `POST /v1/analysis-jobs` | 비동기 분석 접수. 완료 응답이 아님 |
| BE → RunPod | `POST /v1/analysis-jobs/{requestId}/cancel` | 실행 세대에 대한 취소 |
| RunPod → BE | `GET /api/internal/ai/worker-readiness` | callback 수용 준비 확인 |
| RunPod → BE | `POST /api/internal/ai/analyses/{analysisId}/claim` | 실행 권한·lease 획득 |
| RunPod → BE | `POST /api/internal/ai/analyses/{analysisId}/heartbeat` | 실행 lease 갱신 |
| RunPod → BE | `POST /api/internal/ai/analyses/{analysisId}/result` | 결과 검증·저장 |
| 사용자 → BE | `GET /api/training-sessions/{id}/analysis/status` | 완료 상태 polling |
| 사용자 → BE | `GET /api/analyses/{analysisId}` | 저장된 점수·피드백 상세 조회 |

BE의 endpoint는 `https://ai.voice-coaching.site`, AWS nginx의 현재 upstream은 `https://s8hb5b0l5k4qlx-8080.proxy.runpod.net`이다. nginx SNI와 upstream TLS 검증이 켜져 있다. callback base는 `https://api.voice-coaching.site`다. 사용자 JWT와 서버 간 Bearer 토큰은 별개다.

코드 흐름은 TrainingAnalysisRequestService → 발행 port/RunPodOutboxAnalysisJobPublisher → RunPodAnalysisRequestOutboxDispatcher → RunPodAnalysisClient, 그리고 InternalRunPodAnalysisController → AnalysisRunPodCallbackService → 결과 저장이다. 조회는 AnalysisController → AnalysisService → AnalysisResultReader → 응답 DTO다.

현재 JAR의 `runpod_result_v1.schema.json` 내부는 결과 v1/v2를 허용하고 `scoringEvidence` v1/v2/v3를 지원한다. 파일명 v1만 보고 점수가 없다고 판단하면 안 된다. 요청 스키마는 `voice-coaching.runpod-analysis-request.v1`이다.

## RunPod 파이프라인

1. HTTP 접수, claim 및 heartbeat로 실행 권한 관리.
2. S3 오디오 materialize 및 무결성 확인.
3. 대본의 g2pK 기대 음소 생성, 오디오 16kHz mono WAV decode.
4. Seungun 음성 분석과 기대열/CTC 후보열 정렬·검출 근거 수신, 코칭할 음소 한 곳 선택 또는 `COMPLETED_NO_ISSUE` 결정.
5. 영상이 있으면 선택적 영상 adapter 실행. 선택 음소와 연결된 공개 가능 영상 근거를 구성하며 실패·미제공 상태를 구분.
6. v3 기준표로 9개 대분류와 40개 음소별 근거를 집계하고 총점을 결정적으로 계산. CLOVA는 계산된 총점 일치와 최대 3개 관심 항목을 반환하도록 검증받음.
7. 음성·영상·점수 근거를 피드백 입력으로 구성하고 입력 예산에 맞춰 축약. 선택한 음소를 중심으로 CLOVA 서술형 피드백 생성.
8. 출력 검증 실패나 생성 오류 시 승인된 기존 문구로 fallback 가능.
9. 점수가 있으면 HTTP 결과 v2의 `overallScore/scoringEvidence`와 피드백을 BE callback으로 전송. BE가 사용자용 점수표로 변환해 상세 API에 제공.

근거: AI `backend_analysis/service.py:106`, `adapters/seungun.py:204`, `hierarchical_scoring.py:72`, `grounded_feedback.py:48`, `http_runtime.py:280`, `http_models.py:187`.

## 사용자 API로 제공 가능한 발음 피드백

현재 JAR의 AnalysisResultResponseDto에서 `scoreBreakdown`, `scoreHierarchy`를 직접 확인했다. 해당 필드는 현재 로컬 HEAD의 DTO에는 없으므로 운영과 로컬을 구분해야 한다. 세부 변환 로직은 JAR 파일명에 대응하는 저장소 커밋 `d936066`의 AnalysisResultReaderImpl, AnalysisScoreBreakdown, AnalysisScoreHierarchy와 대조했다.

| 항목 | 사용자에게 전달되는 내용 | 조건·한계 |
|---|---|---|
| 서술형 코칭 `summaryFeedback` | 선택 음소에 대한 발음 방법·일반 조음 설명·연습 안내 | `COACHING_READY` 중심. 생성 내용은 가변적이며 모든 종류의 안내를 매번 보장하지 않음. fallback 가능 |
| 총점 `overallScore` | 0~100점, 소수 첫째 자리 | 결정적 근거 점수. 사람의 발음 정확도 확률이 아님 |
| 대분류 `scoreBreakdown` | 9개 기준의 설명·대상 음소·배점·적용 여부·표본 수·0~4 등급·점수 | v2/v3 근거가 저장·검증된 경우. 문장에 없는 음소를 0점 처리하지 않음 |
| 음소별 `scoreHierarchy` | 40개 표지별 등급·환산점수(0/25/50/75/100)·표본 수·관심 표시 | 해당 기대 음소가 없으면 `NOT_APPLICABLE`. 설명에 일치·대치·누락 후보 수 포함 |
| 전체 대응 범위 | 기대 음소 정렬 대응 수준 | 모든 발음이 정확하다는 의미가 아님 |
| 선택 음소 `pronunciationEvidence` | 음소, 기대열 index, 시작/끝 ms, 검출 점수·threshold·근거 상태 | 한 곳의 근거. 시각은 없을 수 있고 검출 점수는 확률/총점과 다름 |
| 영상 보조 `visualSupplement` | 동일 선택 음소와 연결된 승인 영상 주장 식별자와 선택적 입술 관측 | 영상·검증 근거가 있어야 제공. 항상 생성되지 않음 |
| 입술 형상 관측 | 안쪽 벌림, 바깥쪽 벌림, 안쪽 개구 면적, 바깥 윤곽 면적의 정규화 비율과 구간 | 유효한 연결 관측이 있을 때만. `NOT_VALIDATED`, 총점 제외 |
| 입술 움직임 항목 | 닫힘 정도, 열림 전환, 닫힘 전환, 움직임 변화율 패턴의 항목/상태 | 현재 기준표에 measurementKey가 없어 실제 점수·관측값은 제공하지 않음 |
| 분석 outcome | `COACHING_READY` 또는 `COMPLETED_NO_ISSUE` | 후자는 교정 대상 미선택이며 만점·완벽한 발음을 뜻하지 않음 |

계층표의 49개 leaf는 **음소 40 + 전체 대응 범위 1 + 영상 8**이다. 49개 전부가 채점 가능한 발음 항목이라는 뜻은 아니다.

### 9개 음성 채점 기준

| 기준 | 대상 | 기본 가중치 |
|---|---|---:|
| 모음 | ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ | 25 |
| 평음 파열음 | ㄱ ㄷ ㅂ | 10 |
| 경음 파열음 | ㄲ ㄸ ㅃ | 10 |
| 격음 파열음 | ㅋ ㅌ ㅍ | 10 |
| 마찰음 | ㅅ ㅆ ㅎ | 10 |
| 파찰음 | ㅈ ㅉ ㅊ | 10 |
| 비음 | ㄴ ㅁ ㅇ | 10 |
| 유음 | ㄹ | 5 |
| 기대 음소 대응 범위 | 대본 전체의 기대 음소 대응 | 10 |

총점은 적용 가능한 대분류의 `가중치 × 등급` 합을 `4 × 적용 가중치 합`으로 나눈 뒤 100배한다. 등급 4/3/2/1/0은 근거 집계 기준의 95/80/60/40/0% 경계에 대응한다. 이는 인간 청취 정확도와 동일한 비율이라는 뜻이 아니다.

## 제공하지 않거나 확인되지 않은 정보

- 현재 HTTP callback은 transcript, STT confidence, 별도 pronunciationScore, 억양 점수, 속도/WPM, 강세 점수, 쉼 점수, 독립 strengths/weaknesses, 전체 음절 segments를 전송하지 않는다. 공개 DTO에 필드가 있다는 이유만으로 지원한다고 볼 수 없다.
- 전체 음소 분석 근거가 내부에 있어도 모든 근거가 LLM 입력이나 사용자 결과로 전달되지는 않는다.
- 혀 위치나 실제 발음 오류를 직접 관측했다고 단정할 수 없다. CTC 대치·누락은 모델 정렬 후보이며 일반 조음 안내와 구분해야 한다.
- 서술형 피드백 존재만으로 CLOVA 호출 성공을 증명할 수 없다.
- 실제 프론트 화면이 위 점수표와 영상 상태를 모두 표시하는지는 이번 조사 범위에서 확인하지 않았다.

## 검증 및 제한

수행: SSH 읽기 전용 상태 확인, AWS 런타임 transport·nginx 경로 확인, 현재 JAR 계약/공개 DTO 검사, RunPod 인증 health 200, 실제 배포 소스 검사, 주요 10개 파일 해시 대조, 운영 기준표 읽기.

미수행: 새 작업 접수·음성/영상 추론·CLOVA 생성·결과 callback 저장·사용자 JWT 상세 조회·브라우저 E2E. 따라서 이번 health 성공은 새로운 분석의 종단 성공 증거가 아니다. 원격 서비스나 모델은 변경하지 않았다. 기존 사용자 삭제·미추적 문서는 보존했다.

관련 과거 기록: [피드백 전달 개선](../clova-feedback-transfer-result-20260918.md), [아키텍처 점검](ai-feedback-architecture-audit-20260918.md). 이 문서의 실측과 과거 기록은 구분한다.
