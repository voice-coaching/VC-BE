# 분석 파이프라인 배포와 접수 보호

2026-10-03 구현. 세션 107 장애에서는 진행 중인 분석의 상태 GET이 단일 Spring
프로세스 교체 중 502가 됐고, 별도로 새 JAR와 이전 AWS verifier/RunPod의 계약이
달랐다. JAR의 일반 health만 확인하는 배포를 호환 묶음 확인으로 교체한다.
이 문서는 운영 배포 완료 기록이 아니다.

AI 배포 진단/overlay 의존 소스: [intelligentAI 4a63fd0](https://github.com/voice-coaching/intelligentAI/commit/4a63fd0e70986c6fc4a8f365c4f7e71791d90dbb).

## 변경 위치

- `scripts/deploy.sh`, `.github/workflows/deploy.yml`: JAR SHA-256 전달, 최신 develop
  확인, 호스트 잠금과 release 상태 전환. 실행 중인 배포를 취소하지 않는다.
- `scripts/deployment/common.py`: 읽기 전용 PostgreSQL 집계, 제한된 HTTP 조회,
  원자적 상태 기록. 토큰은 실행 중인 서비스 환경에서 메모리로만 읽는다.
- `scripts/deployment/probes.py`: verifier 원본/과거 및 새 pin 8개, schema 9개,
  RunPod 앱 묶음과 하네스, 실제 FE 문서에 참조된 JS 파일의 digest를 확인한다.
- `scripts/deployment/release.py`: `check`, `deploy`, `hold`, `activate`, `resume`,
  `abort`, `rollback` 단계. 인스턴스 `/var/lib/alpha-deploy/deployment.lock`을 공유한다.
- `scripts/deployment/nginx-analysis-maintenance.conf`: 신규 analyze/retry와
  feedback/regenerate만 503 + Retry-After 15로 차단한다. GET, callback, journal,
  heartbeat, 결과 반영, 인증, 업로드에는 적용하지 않는다.
- `AnalysisSubmissionAdmission` / `FileAnalysisSubmissionAdmission`: 신규 분석과
  재생성 service 진입에서도 같은 marker를 확인한다. 내부 readiness에는 연결하지
  않으므로 양쪽 admission 대기가 순환하지 않는다. 직접 서비스 응답은 기존
  ANALYSIS_INTEGRATION_UNAVAILABLE 503 계약을 사용한다.
- `CanonicalBackendReadiness`: 실패 단계가 바뀔 때 고정된 진단 label만 기록한다.
  verifier 출력, 토큰, URL, 근거 원문은 기록하지 않는다. 공개 reason enum은 유지한다.

## 최초 운영 준비 (필수)

운영자는 먼저 AI 변경을 별도 배포해야 한다. RunPod 인증 GET `/health/deployment`와
gateway 경로, 영구 overlay, scored core/H5, AWS verifier 전체 묶음이 필요하다.
구버전 Pod의 404를 작업 0개로 해석하지 않는다. 현재 불일치 상태를 최초 복구하는
작업은 자동 CI의 선행 조건이며, 이 PR 병합만으로 완료되지 않는다.

기존 `intelligentAI`의 `build_canonical_scored.py`,
`export_canonical_verifier_frozen.py`, `stage_backend_verifier_bundle.py`로 새 verifier를
비활성 경로에 준비한다. 원래 core/S7/H5와 scored-core/scored-H5를 함께 보존한다.
RunPod 앱은 고정 Git SHA의 `build_canonical_overlay.py`로 포장한다.
`inspect_canonical_overlay.py`의 읽기 전용 관측과 설치 직전 beforeSha 검증이 모두 필요하다.

AWS에 다음을 준비한 후 일반 자동 배포를 사용한다.

1. `/etc/nginx/snippets/alpha-analysis-maintenance.conf`에 저장소의 snippet을 설치한다.
   `api.voice-coaching.site`의 실제 server 블록에서 다른 regex location보다 먼저 include한다.
   `/api`를 우선하는 `^~`/exact location으로 우회되지 않는지 운영자가 확인한다.
   배포 도구는 snippet 원본 일치, include 존재, nginx 설정 검사와 reload 성공을 요구한다.
2. `/etc/alpha-deploy/runtime.json`을 root 소유로 작성한다. 파일과 모든 상위 디렉터리는
   symlink가 아니고 group/other 쓰기를 허용하지 않아야 한다. 비밀값은 넣지 않는다.
3. `/opt/alpha-canonical/releases/<verifierBundleSha256>` 및 관련 파일을 root 소유로
   준비한다. 서비스 사용자 `ec2-user`가 읽을 수 있어야 한다. 실제 설정 파일은
   `/etc/alpha-canonical-secrets/backend-runtime.env`(0600)다.
4. 표준 Python 3.9+와 `psql`, `nginx`, `systemctl`이 필요하다. semantic verifier는
   기존 설정의 Python 3.12와 고정 의존성을 사용하며 서비스 사용자로 검사한다.
5. 서비스의 `analysis.deployment.hold-file`은 기본 경로
   `/var/lib/alpha-deploy/analysis-admission.closed`를 유지한다. nginx/도구와 다르게
   설정하지 않는다. 폴더는 nginx와 서비스가 읽을 수 있는 root 소유 0755다.

`runtime.json`은 정확히 다음 필드를 가진 version 1 문서다. 실제 관측값으로 작성하며
자리표시자나 브랜치 이름을 digest 대신 넣지 않는다.

| 필드 | 값의 출처 |
| --- | --- |
| `version` | 정수 1 |
| `aiGitRevision` | 배포한 AI 소스의 40자리 Git SHA |
| `runpodBundleSha256` | 설치한 canonical overlay JSON의 SHA-256 |
| `verifierBundleSha256` | AWS verifier `deployment.json` 바이트의 SHA-256 |
| `pins` | verifier `--check-installation`의 coreManifestSha256, llmManifestSha256, llmLockSha256, h5ManifestSha256, h5LockSha256, scoredCoreManifestSha256, scoredManifestSha256, scoredLockSha256 |
| `schemaDigests` | 고정 9개 schema 파일명 → SHA-256 객체 |
| `frontend` | `origin`, `revision`(Git SHA), `pagePath`, `assets`(실제 HTML에 포함된 `/_next/*.js` 경로 → SHA-256 객체) |

FE Git revision과 Vercel deployment의 연결은 운영자가 확인하여 기록한다. 도구는 실제
HTML/JS digest를 검증하지만 Git SHA와 번들의 대응을 독립적으로 증명하지는 않는다.
일반 레이아웃 chunk만 고르지 말고 분석/점수 계약을 포함한 해당 페이지 chunk를 포함한다.

## 배포 단계

CI는 `bootJar -x test`로 컴파일·포장한다. 자동 회귀 테스트는 실행하지 않으며 수동 QA는
개발자가 담당한다. 새 의존성이나 runtime profile이 없으면 활성화 전에 실패한다.

```bash
sudo bash scripts/deploy.sh "$RELEASE_SHA" "$JAR_SHA256"
# 같은 도구의 읽기 전용 사전 확인
sudo python3 scripts/deployment/release.py check "$RELEASE_SHA" "$JAR_SHA256"
```

입력 JAR는 `/tmp/alpha-<SHA>.jar`다. 첫 검증 후 marker를 생성하고 DB의 PENDING/
PROCESSING, dispatch/cancel outbox, receipt/callback 검증, apply, effects, active lease,
미확인 ACK, 현재 실행의 journal/upload, analysis_results의 진행 트랜잭션 및 Pod 슬롯이
모두 0인지 5초 간격으로 두 번 확인한다. 이미 끝난 과거 journal 원본은 지우거나
미완료 작업으로 다시 만들지 않는다. 20분 초과나 조회 실패 시 기존 프로세스를 유지하고
marker를 남긴다. 강제 FAILED, lease 초기화, 강제 종료로 drain을 통과시키지 않는다.

JAR와 verifier 경로를 교체한 뒤 AWS supported/admission, Pod executor/admission/
reasonCode 및 schema 9개를 15초 간격으로 세 번 확인한다. 마지막에 FE와 drain을
재확인한 뒤 접수를 연다. 단일 프로세스 재시작의 GET 공백은 남으므로 FE의 제한된
조회 재시도가 필요하다. 이 변경을 무중단 배포로 표현하지 않는다.

이미 진단 endpoint가 있는 환경에서 schema 전환을 조정할 때는 동일한 SHA/profile로
`hold` → `activate` → 별도 RunPod 묶음 활성화 → `resume`을 실행한다.
중간의 canonical admission 불일치는 접수를 닫은 상태에서만 허용한다.
상태는 `/var/lib/alpha-deploy/releases/<SHA>/state.json`, 마지막 성공 묶음은
`/var/lib/alpha-deploy/current.json`에 기록한다. 비밀 설정 백업은 해당 root 전용
release 디렉터리의 `backend-runtime.env.backup`에만 두고 artifact에 넣지 않는다.

## 실패 복구와 제한

`abort`는 JAR 변경 전이며 마지막 호환 manifest의 readiness를 확인한 경우에만
marker를 해제한다. `rollback`은 먼저 운영자가 이전 manifest의 RunPod/FE를 복구한
상태에서 pin·drain을 확인하고 이전 JAR/기존 설정 백업을 복원한다. 그 뒤 같은
readiness 조건을 충족해야 접수를 연다. 이전 manifest가 없으면 추측하여 복구하지 않는다.

전환 도중 프로세스가 죽었거나 state/marker가 모순되면 자동 재실행을 중단한다.
특히 Backend 자체가 기동하지 않아 실행 환경을 읽을 수 없는 경우 자동 rollback은
실행하지 않는다. 운영자는 marker를 유지한 채 기존 비밀 설정과 state의 이전 JAR를
확인해 서비스를 복구하고, 호환 묶음 전체의 readiness를 다시 확인한다. DB 비밀번호나
API 토큰을 별도 복구 JSON에 복제하지 않는다. 새 결과가 이미 저장된 후에는 이전
reader가 읽을 수 있는지 확인 없이 하네스/JAR를 되돌리지 않는다. DB와 근거는 삭제하지 않는다.

## 확인 범위

Java 컴파일, Python 구문, 계약/경로 및 문서 정적 확인 범위다. 운영 재시작, 실제 DB
drain, 음성/GPT/callback 생성, 브라우저·회귀 QA를 수행한 기록이 아니다.
개발자는 격리한 개발 환경에서 정상 전환, 진행 작업 대기, 누락 artifact, timeout,
실패 단계별 복구, 신규 요청 차단과 기존 callback 지속을 수동 확인해야 한다.
