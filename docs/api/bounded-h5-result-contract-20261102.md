# 운영 bounded H5의 v6 결과 분류 보정

AI J406의 운영 H5는
`25531c9e52c1088c9cdceee82a5e2d57d52c46da4b4cd93cac75eea87d6ade60`이다.
이 identity는 v6 sourceIdentity enum에 등록돼 있지만 result/audioResult의 scoring·GPT
분류 조건에서 빠져 있었다. AI의 최근 실제 두 요청은 CORE·EXPRESSION·PREFLIGHT를
통과한 뒤 RESULT_PUBLISH에서 CANONICAL_SCHEMA_INVALID로 실패했다.

이 변경은 `docs/contracts/runpod_result_v6.schema.json`의 네 classification enum에
이미 운영 중인 H5만 추가한다. score·coaching·selection 필드를 느슨하게 바꾸거나,
인증·소유권·의미 검증·readiness를 제거하지 않는다. endpoint와 DTO, wire version은 동일하다.
`build.gradle`이 이 계약 파일을 resources로 포함하므로 Backend 배포에도 반영해야 한다.

수정 schema SHA-256:
`f17fd6ff70cd4fa3fcb1ee91d878cdf7fef0b2880159e4da17b5d19a46b9e40f`.
AI 수정 브랜치의 동일 파일과 byte 단위 일치를 정적으로 확인했다.

Backend 담당자는 AI의 coordinated schema 배포와 semantic verifier의 계약 사본을 함께
맞춰야 한다. 양쪽 digest가 다르면 admission이 차단되는 것이 정상이며 이를 우회하지 않는다.
AI 병렬 실행기 변경은 AI 저장소 소유이고 이 PR의 Java 실행 정책 변경은 없다.

사용자 지침에 따라 테스트·실제 녹음·AI 생성·브라우저 자동화는 실행하지 않았다.
JSON 구문·diff·계약 bytes/hash만 정적으로 확인했다. 병합·배포와 개발자 QA는 별도다.
