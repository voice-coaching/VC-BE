# 관리자 승급 시험 관리 검증

범위: GET/PATCH 정책 설정, 기존 관리자 인증, 콘텐츠 보호, 기존 승급 합격 판정 회귀.

```bash
./gradlew test --tests '*AdminTitleExamPolicyIntegrationTest' --tests '*AdminApiIntegrationTest' --tests '*TitleIntegrationTest.passingCriteria*'
```

`AdminTitleExamPolicyIntegrationTest`는 서명 없는 헤더 차단·일반 회원 차단·no-store·저장·버전 충돌·후보 문장 변경·점수 범위·시작 칭호·미공개/다른 분류·없는 문장·기존 시험 기준 유지·연결 콘텐츠 숨김/삭제 보호·감사 실패 롤백을 확인한다.

PostgreSQL 검증은 `VC_BE_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:<임시 포트>/postgres`와
`VC_BE_TEST_POSTGRES_USER=postgres`를 설정하고 `AdminTitleExamPolicyPostgresTest`를 실행한다.
테스트는 임의 스키마에서 V66→V67을 적용해 기존 합격 점수 보존, 버전 기본값 0, NOT NULL을 검증하고 해당 스키마만 정리한다.
운영 DB URL을 지정하지 않는다.

배포 후에는 관리자에서 새 시험 문장을 등록·공개하고 목표 칭호에 연결한다. 저장 후 다시 조회해 문장과 점수를 대조한다.
변경 전 생성한 시험은 이전 기준을, 변경 후 생성한 시험은 새 기준을 사용하는지 테스트 계정으로 확인한다.
프론트와 연결한 운영 환경의 실제 AI 분석·채점 성공 여부는 별도로 확인한다.

2026-11-07 실행 결과: 관리자 신규 통합 9개 + PostgreSQL 마이그레이션 1개 + 기존 관리자 API 20개 + 승급 합격 판정 회귀 6개, 총 36개 통과·제외 없음. bootJar 성공. 로컬 임시 PostgreSQL만 사용하고 종료했으며 운영 DB를 변경하지 않았다. 전체 저장소 테스트 통과를 의미하지 않는다.
