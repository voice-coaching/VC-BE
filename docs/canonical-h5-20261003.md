# Canonical H5 표현 하네스 수용

**2026-10-03 후속:** 사용자 직접 배포 승인으로 source `68ecde1` JAR과 아래 verifier를 AWS에 활성화했다. 14:55 KST RunPod H5와 공유 schema 일치·양쪽 canonical 접수 ON을 확인했다. PR은 병합하지 않았고 실제 녹음·GPT·전체 callback QA는 미수행이다. 아래 비활성 준비 기록은 전환 전 시점이다.

사용자가 기존 canonical v4에 H5의 짧은 행동 총평을 선택했다. v4 요청 프로필과 공개 DTO, 기존 S7 결과는 유지하고 결과 schema에 H5의 manifest/lock/prompt 조합을 추가한다. 서로 다른 하네스의 해시를 섞은 조합은 거절한다.

- H5 manifest: `9f10296b6944249ded9f5ce2ccfdfa7c53b6e4af670999fe68e9e477e97eaf45`
- H5 lock: `93636f0c4befe7f1e34358e74b1077cc94cb2b82064781485b0183c53357d2f5`
- H5 prompt: `6f9e8515ae083a62e0500a03e3c2cef073ae78eb696bed8b5857a14b551ff1fd`
- 공유 결과 schema SHA: `11f7c609421a0c80cb337cb23c1c203cef540f28eea7fce67b16f37577029d5b`

독립 verifier가 S7와 H5를 모두 검사한 설치 응답만 readiness에 수용한다. verifier release에는 기존 `frozen/llm`의 형제 `frozen/canonical_h5_20261003`와 AI 저장소의 새 `canonical_harness.py` 등 검증기 의존 파일이 필요하다. 원본 evidence association의 manifest에 따라 검증기를 선택하며 기존 S7 fallback 검증도 유지한다.

독립 의미 검증을 통과한 H5의 ACCEPT/READY 결과는 순서대로 action만 줄바꿈해 기존 summaryFeedback에 저장한다. 후보 최대3, action 한 문장·UTF-16 140 한도는 RunPod와 독립 verifier에서 함께 확인한다. 원본 expression과 근거는 바꾸지 않는다. non-ACCEPT·시스템 실패 메시지는 유지한다. DB migration은 없다.

운영 적용은 진행 중인 분석을 보호한 뒤 AWS 새 verifier와 이 JAR, RunPod 새 schema·H5 boot 설정, FE action-only 총평 순서다. schema digest가 다른 동안 접수는 fail-closed다. RunPod만 먼저 배포하면 기존 Backend가 새 identity를 거절한다. 원복 후에도 저장된 H5 결과를 읽으려면 이 reader를 유지한다.

검증: Java 21 `bootJar -x test --no-daemon` 빌드 성공. 자동 테스트·실제 분석·품질 QA는 미수행이다. `develop` PR 인계이며 운영 배포는 별도다.

AWS에 비활성 검증기 release `021e17aa5e3bb51170b2cd58cd86d70739177ea82fef324d4cc310d3cd490ed4`를 준비했다. `/opt/alpha-canonical/releases/` 아래에 있고 서비스 사용자 `--check-installation`에서 S7/H5 모두 READY다. 활성 설정은 변경하지 않았다.
