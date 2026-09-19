# CLOVA 계층형 채점 v3 운영 배포·실영상 검증

확인일: 2026-09-18 KST. 사용자 지정 `testvideo1.mp4`로 수행했다. 인증값·서명 URL·원본 분석 응답은 이 문서에 포함하지 않는다.

## 결과

**배포된 v3에서 실제 분석, CLOVA 호출, AWS 결과 저장과 공개 API 조회가 성공했다.** 브라우저 UI를 조작한 검증은 아니며, 프론트가 사용하는 공개 API 흐름을 테스트 계정으로 실행했다.

| 항목 | 관측 결과 |
| --- | --- |
| 테스트 대상 | 세션 34 / 녹음 16 / 분석 15 |
| 업로드 | 영상 2,097,382 bytes, S3 PUT 200 |
| 등록·정규화·선택 | 각 200, 음질 `PASS` |
| 분석 접수 | POST `/api/training-sessions/34/analyze` 200 |
| 진행 상태 | `PROCESSING` → `COMPLETED`, 실패 사유 null |
| 완료 상세 | GET `/api/analyses/15` 200 |
| 완료 결과 | `COACHING_READY`, `overallScore=62.50` |
| 채점 계약 | `clova-phone-rubric-v3`, 저장 근거의 generator=`hyperclova` |
| 공개 계층 | 11개 대분류 / 49개 소분류 |
| 소분류 상태 | SCORED 19 / NOT_APPLICABLE 22 / UNAVAILABLE 8 |
| 결과 콜백 | POST `/api/internal/ai/analyses/15/result` 200, 완료 결과 DB 저장 확인 |
| 입술 근거 | `visualSupplement=null`; 입술 8개는 미채점 |

접수 DB 생성 시각 **15:31:40.835641**, 완료 저장 **15:33:29.137161 KST**. 접수부터 완료 저장까지 **108.301520초**다. 업로드 시간과 마지막 클라이언트 조회 왕복 시간은 포함하지 않는다.

## 실제 CLOVA 호출 근거

운영 loopback `/v1/chat/completions`를 사용하는 워커의 실행 중 `/health.last_completion` 변화를 관측했다. 아래는 해당 분석 진행 중 새로 기록된 완료값이다. 이전 호출의 baseline은 제외했다.

| 작업 | 입력 토큰 | 출력 토큰 | 생성 시간 | 종료 사유 |
| --- | ---: | ---: | ---: | --- |
| 근거 기반 피드백 | 1,787 | 239 | 60.129초 | stop |
| v3 채점 검토 | 1,981 | 59 | 15.695초 | stop |

저장된 v3 점수 근거는 CLOVA 반환 JSON의 모델 identity, 총점 및 우선 확인 항목 검증을 통과한 결과다. 우선 확인 항목은 `vowels.ae`, `plain_stops.d`, `fricatives.ss`였다. 프롬프트 입력 4,096토큰, 출력 512토큰 제한은 변경하지 않았다.

운영 서버의 기존 상태 API는 완료 토큰·시간을 제공하지만 요청별 LLM 원문 로그는 제공하지 않는다. 위 관측만으로 모든 미래 요청의 피드백 품질이나 fallback 미사용을 일반화하지 않는다. 배포 전 별도 디렉터리에서 같은 영상으로 수행한 실호출에서는 채점 HTTP 200, 피드백 HTTP 200, `validated_grounded_plan`, fallback 사유 null을 직접 확인했다. 이 별도 CPU 검증과 운영 CUDA 분석은 구분한다.

## AWS 사전 영향 검토

- [BE PR #82](https://github.com/voice-coaching/VC-BE/pull/82)는 이미 병합돼 있었고, [운영 배포 실행](https://github.com/voice-coaching/VC-BE/actions/runs/35311123777)이 성공한 상태였다. 운영 JAR은 병합 커밋 `d936066cba1d70bc152eb309b1c3614c71a5bdd3`와 일치했다. JAR 내부 v3 검증 클래스와 기준표도 확인했다.
- `alpha-backend`, nginx는 active. 루트 디스크 약 7.8 GiB 여유, 메모리 available 약 548 MiB. 사전 최근 로그에 뚜렷한 오류가 없었다.
- callback worker-readiness 200. 기존 `runpod_http`, 동의 버전 및 15분 실행 제한·90초 lease를 유지했다.
- 처리 중 분석 0, 미발송 outbox 0을 확인했다. 별도로 남은 PENDING 1건은 8월 생성·실행 ID 없는 기존 레코드로, 수정하지 않았다.
- 이번 RunPod 전환에 DB migration, EC2 서비스 재시작, IAM/S3 정책 변경은 필요하지 않았다.

## 적용 범위와 복구

- [AI PR #13](https://github.com/voice-coaching/intelligentAI/pull/13)의 병합 리비전 `fbca0c59d174a36fee6becc44185a8e229d31858`를 적용했다.
- Git 원본 파일 해시와 기존 배포 파일을 대조했다. 첫 배포 묶음의 Windows CRLF 변환은 사전 검사에서 차단됐으며, LF 원본으로 다시 생성해 통과한 뒤 적용했다.
- 변경한 코드·기준표·프롬프트·schema를 실행 사본과 영구 overlay에 적용하고 manifest 및 HTTP deployment attestation을 갱신했다. `active-hierarchy.md` symlink와 기준표 해시 일치도 확인했다.
- `AI_ANALYSIS_CLOVA_RUBRIC_REVISION=clova-phone-rubric-v3`, `AI_ANALYSIS_CLOVA_SCORING_ENABLED=true`를 적용했다. 부팅 때 비공개 주입 설정이 source 환경파일을 재생성할 수 있으므로 두 비밀 아닌 값을 영구 서비스 기본 설정에도 명시했다. 실제 Pod 재부팅 검증은 실행하지 않았다.
- 신규 analyze/retry 경로만 잠시 503 + Retry-After로 보호하고, 기존 작업이 없는지 다시 확인한 뒤 API 워커만 정상 종료·재기동했다. 로그인·조회·내부 콜백 경로는 유지했다.
- CLOVA 모델 PID 20312는 유지됐다. 모델 가중치, 모델 pin, threshold, 재학습, 토큰 제한은 변경하지 않았다.
- AWS→RunPod health/services에서 새 리비전과 ready를 확인한 뒤 접수를 재개했다. AWS nginx 설정은 배포 전 원본과 바이트 단위로 동일하게 복원했다.
- 배포 전 복구본: RunPod root 전용 `/root/.config/intelligentai/backups/hierarchy-v3-20260918-fbca0c59`. 실패 시 기존 코드·manifest·환경·attestation 원복과 readiness 확인 절차를 준비했다. 이번 배포는 성공해 원복하지 않았다.
- 롤백 시 이미 저장된 v3 결과를 읽는 AWS 백엔드는 유지해야 한다. 워커 v2 복귀와 구형 BE 배포는 같은 작업이 아니다.

## 검증 중 발견 사항과 한계

- 사용자 문장 생성 API는 503을 반환했다. 운영 설정을 바꾸지 않고 이전에 승인받은 동일 대본의 테스트 콘텐츠 1을 사용했다. 세션 생성 동안만 PUBLISHED로 전환하고 즉시 HIDDEN으로 복원했다. 이 별도 기능의 503 원인·복구는 이번 v3 검증 범위에 포함하지 않았다.
- 검증 출력기의 `stage` 인자 충돌은 출력기에서 수정했다. 분석 15는 계속 실행됐으며 재업로드·중복 analyze 없이 기존 작업 조회를 재개했다. 재개 스크립트의 실행 시간을 전체 분석 지연으로 쓰지 않았다.
- 배포 이후 확인 구간에 backend ERROR/SQLState 로그 0건, 분석 15 결과 콜백 200, 처리 중 작업 0, outbox 14건 모두 PUBLISHED를 확인했다.
- 입술 관측 획득, 입술 점수, 재시도·중복 ACK, 모든 문장·49개 항목의 품질은 이 영상 한 건으로 검증한 것이 아니다. 기대 음소가 없는 항목은 0점이 아니라 NOT_APPLICABLE이다.
- 프론트 배포나 브라우저 렌더링 검증은 수행하지 않았다. 현재 공개 API의 v3 계층형 결과를 확인했다.
- 전체 분석 응답의 로컬 복사는 자동 승인 검토에서 민감한 결과 반출을 이유로 거절됐다. 원문은 RunPod의 비공개 검증 디렉터리에 보존하고, 이 문서는 상태·집계·배포 근거만 기록한다. 사용자 키 사본은 변경·삭제하지 않았다.

## 후속 조치 — 사용자 문장 생성 503 해결

2026-09-18 15:48~15:51 KST, 사용자의 명시적 요청에 따라 EC2 설정을 수정하고 검증했다.

- 원인: `/etc/alpha-backend.env`와 실행 Java 프로세스에 `CUSTOM_CONTENT_ENCRYPTION_KEY`가 없었다. `PrivateTextEncryption.requireConfigured()`는 Base64 디코딩 결과가 32바이트가 아니면 503 `TEMPORARY_UNAVAILABLE`을 반환한다. 영상 처리 이전의 문장 저장 준비 단계에서 발생한 오류다.
- 사전 검사: 암호화된 기존 사용자 문장 0건, 실행 중 분석 0건, outbox 14건 모두 PUBLISHED. 기존 암호화 데이터의 키 교체 문제는 없었다.
- EC2 안에서 암호학적 난수 32바이트를 생성하고 Base64 값으로 운영 환경파일에 등록했다. 파일 권한은 root 전용 0600이다. 키 값은 출력하거나 저장소에 기록하지 않았다.
- 변경 전 설정 백업: `/etc/alpha-backend.env.before-custom-encryption-20260918T064756Z`. **이 백업은 신규 키 등록 전 파일**이므로 복원하면 사용자 문장 기능이 다시 비활성화된다. 새 키는 이후 저장된 문장의 복호화에 필요하므로 유지해야 한다.
- `alpha-backend` 재시작 후 로컬·공개 worker-readiness 모두 200. 실행 프로세스에 32바이트 키가 운영 환경파일과 동일하게 주입된 것을 값 노출 없이 확인했다.
- 테스트 사용자 26으로 POST `/api/practice-contents/custom` **201**, 생성 콘텐츠 **2**. GET `/api/practice-contents/2` **200**, 제목·대본 원문 일치.
- 동일 Idempotency-Key 재요청은 같은 콘텐츠 ID를 반환했고, 무인증 조회는 **401**이었다.
- DB 읽기 전용 검사: 공개 대본 열은 `[private]`, 사용자 제목·대본은 `v1:` 암호문으로 저장되며 대본 평문을 포함하지 않음을 확인했다. 암호문 원문도 출력하지 않았다.
- 재시작 완료 이후 확인한 ERROR/SQLState 로그는 0건. 이번 후속 검증에서 새 음성 분석은 실행하지 않았다.
