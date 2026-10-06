# Seungun → GPT 채점·코칭 계약 추가

2026-10-06 사용자 요청에 따라 RunPod가 Seungun 원본 관측과 채점표를 GPT에 전달한다. Native 보정 정책과 서버 사전 채점표를 선행조건으로 요구하지 않는다.

신규 core `20acb7275aa06d0be4aca1a567420ea603c0eec084e50c651c96501903034f1b`, harness `6a3bf7c872e718086cb9add4d48412c825188310cdc9eb394089301dd784ff57`, lock `3f4ce0a44ae75192fe7fea3b03b879c21ab0b2be7cc0a620522c60c2155b674b`를 기존 식별자와 함께 수용한다. 기존 AWS 운영 caf8c33의 계약·배포 도구 수정도 포함해 develop에서 운영 코드를 퇴행시키지 않는다.

DB 변경은 없다. 점수 필드와 9항목 배점표는 유지하며 신규 harness의 수치 판정 주체는 GPT다. 비동기 이력 저장은 결과 공개 이후 처리한다. 운영 전환은 새 Python verifier와 RunPod 릴리스, 같은 schema digest를 함께 활성화한다.

검증: Java 21 `bootJar -x test` 컴파일. 자동 회귀 테스트와 실제 음성 품질 QA는 실행하지 않는다.
