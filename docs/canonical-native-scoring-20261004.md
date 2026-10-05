# Native 전체 음소 채점 계약

2026-10-04. 기준 develop `836e49a`, 작업 브랜치 `feat/native-score-contract-20261004`. 소스 변경이며 배포·추론 QA 완료 기록이 아니다.

- intelligentAI의 Native DRAFT schema/identity를 동기화했다. `phone-rubric-native-v1`을 기존 revision과 구분해 수용하고, 새 H5의 교정 안내를 요약에 보존한다. 기존 revision과 점수를 바꾸지 않는다.
- Native manifest/lock 필드가 설치 확인 응답에 있으면 셋 모두 고정 SHA와 일치해야 한다. 이전 verifier 설치의 필드 생략은 기존 동작을 유지한다. schema SHA가 양쪽에서 같아야 신규 접수가 가능하다.
- Native core SHA: `9e2a2107db7d30fe8910c8984687974d85b95120760e2bcce53d57640a5d5a32`.
- Native H5 SHA: `358365399f7445ab0a77797ce46619979955503c4faac8185724e2225aa5205c`.
- Native H5 lock: `798b47b6f5355b120141a7fa730350739298082549036233ea5804226dbad80c`.
- 결과 우선 공개와 비동기 DB/B2 보존 순서는 유지한다. raw FP32 logits는 비공개 CORE 안에만 포함하며 별도 DB 모델/공개 필드를 추가하지 않는다. offline verifier는 NumPy 2.4.6의 고정 FP32 계산으로 검증한다.

정책은 현재 DRAFT이고 RunPod 실행 승인 목록은 비어 있다. 보정/held-out 검증과 개발자 QA 후 새 immutable 릴리스·SHA를 등록해야 한다. 접수를 끈 상태에서 verifier 환경/스키마/RunPod/FE를 함께 준비하고 readiness가 일치한 뒤 전환한다. 이 변경만 develop에 병합하면 자동 배포되므로, 운영 준비 없이 병합하지 않는다.

JDK 21과 기존 Gradle 캐시로 `compileJava --offline --no-daemon`이 통과했다. 자동 테스트·실제 분석·DB 변경·커밋·푸시·PR·배포는 수행하지 않았다. 계약 정적 확인 범위는 intelligentAI 구현 인계 문서에 기록한다.
