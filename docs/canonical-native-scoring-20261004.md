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
# 2026-10-05 준비 지연 개선 패키지 추가 등록

Native v2의 core/H5/lock 조합을 결과 스키마와 설치 확인에 추가한다. v1의 정확한 조합과 기존 Native 미설치 응답도 계속 허용한다. 서로 다른 버전의 해시를 섞으면 거절한다. API 요청·DB 마이그레이션·운영 설정은 변경하지 않는다.

- core: `68cda53df72be9dace15f3829a840c27f40efbbfee1a561f8e58d314b4991aad`
- H5: `7dfc26fb088300165b4a564847d0a489092b0ab830ef591943a40f31ecb85747`
- lock: `93ccb06cafb9128f4ae02f111ca4bfb599305f84ecce01e41971e69822ba1ec6`

이 등록은 Native 추론 승인이 아니다. AI 보정 정책은 DRAFT이며 실행 allowlist는 비어 있다. 승인된 패키지는 새 해시로 다시 등록해야 한다. 이번 확인 범위는 Java 컴파일과 AI/BE 스키마 정적 일치 확인이며 실제 분석·자동 회귀 테스트·배포는 실행하지 않는다.
