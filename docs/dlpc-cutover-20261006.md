# DLPC CUDA 소스 등록과 TTS 전환 준비

2026-10-06. 대상 브랜치는 `develop`. 실행 승인·배포 완료 문서가 아니다.

## 변경

- `CanonicalSemanticVerifier`: 기존 Native v1/v2를 보존하고 현재 AWS/RunPod의 v4와
  DLPC CUDA v2의 core/H5/lock 조합을 추가한다. 부분 tuple 또는 혼합 tuple은 거절한다.
- `docs/contracts/runpod_result_v4.schema.json`, `runpod_result_v5.schema.json`:
  AI와 같은 닫힌 조합·native 분기를 사용한다. 과거 identity는 보존한다.
- CUDA 소스 등록은 분석 실행 승인이나 수치/음성 품질 승인이 아니다.

## 후보와 확인 근거

CUDA core `4d29ddf6da45d43de6ceb0b07b70cecaf291ae5bc92b700ece6b320a8e6c423d`,
H5 `de8f66aa7688404f2f59fc17e67dd2c250cf746d58f9697bebd8fee87095f5f9`,
lock `fc48fc935df8c4fb90c0a9345651c047a320da220fe46c12e6e00d395a6c556d`.

AWS의 비활성 verifier 번들
`56a51e940337b41e5a9731f7105289a3e495a434fa06f59ea7dc45750753d2a3`는
`--check-installation`에서 위 CUDA tuple과 READY를 반환했다. 실제 녹음 추론/QA가 아니다.
활성 verifier, JAR, DB, nginx는 변경하지 않았다.

TTS 후보 revision `melo-ko-practice-dlpc-r1`, fingerprint
`ace97bc52df31f304513bc5f18177669723fe996f5cd19235691a5449d9f3766`.
AWS root 전용 `/root/.config/intelligentai/dlpc-tts-candidate-20261006/backend.env`는
기존 env의 revision/fingerprint 두 항목만 바꾼 **비활성 후보**다.
실제 음성 QA와 새 revision reconcile을 수행하지 않았다.

## 전환 조건

1. AI의 재생성 복구 hook·AWS 비밀 재주입 및 승인 release 복구 경로 확정.
2. 개발자 CUDA/TTS QA, AI 실행 승인, 본 PR 및 AI 계약 변경 배포.
3. AI·Backend schema digest, offline verifier identity, TTS 합성 manifest 일치 확인.
4. 새 analysis/direct 접수를 닫고 기존 lease/handoff/callback/TTS 작업을 drain.
5. env·verifier·upstream 원본 digest를 확인한 뒤 후보를 적용하고 readiness 확인 후 재개.

`develop`은 현재 운영 Native v4보다 뒤처져 있었으므로 이 PR에는 v4 등록 복구도 포함한다.
운영 JAR를 develop 빌드로 무조건 덮어쓰지 말고 현재 배포 revision과 담당자 검토를 맞춘다.
컴파일 확인은 테스트/품질 QA 통과와 구분한다. 자동 회귀 테스트는 실행하지 않는다.

검사 결과: JDK 21, 기존 Gradle 캐시로 `compileJava --offline --no-daemon -x test -x check`
성공. `git diff --check` 통과. AI와 v4/v5 schema 바이트 일치를 확인했다.
v4 SHA `00e5b8104efc1539248246a3235622574228f1363930767160b62e248049d90c`,
v5 SHA `b21618ba31147539d0783a780241bd696545250d146d1fea7caa3b57c81ce977`.
