# 인기 뉴스 반복 조회400 수정

## 재현과 원인

2026-11-02 최신 main 65215a5 기준, `GET /api/practice-contents/popular?type=NEWS&limit=10`은 프론트 프록시와 백엔드 직접 호출 모두400과 result=false, message=잘못된 입력값입니다를 반환했다. Vercel deny 응답과 다른 백엔드 오류이며 프론트 query는 정상이다.

커밋 e597761에서 날짜 행 생성 SQL을 ON CONFLICT DO NOTHING에서 DO UPDATE로 바꾸었다. 기존 날짜를 UPDATE해도 JDBC update count=1이어서 service가 신규 생성으로 판단하고 일별 items를 다시 INSERT했다. V61에서도 남아 있는 news_popularity_items의 PRIMARY KEY(ranking_date,content_id)가 중복되어 DuplicateKeyException(SQLSTATE23505)이 발생한다. 기존 GlobalExceptionHandler가 DataIntegrityViolationException을400으로 매핑하여 입력 오류처럼 보였다.

격리 PostgreSQL의 V61 스키마에서도 동일하게 재현했다. 수정 전 회귀 테스트4개 중 반복 HTTP조회·동시 최초 조회·재시작 후 조회3개가 중복 키/HTTP400으로 실패했고 초기 생성 실패 시 rollback 테스트는 통과했다.

## 변경

fix/popular-news-snapshot-idempotency 브랜치는 JWT 수정 PR170을 포함한 최신 main 65215a5에서 분리했다. NewsPopularityService의 날짜 충돌 처리를 DO NOTHING으로 복구한다.

- 날짜 행을 새로 삽입한 요청만 영향 행 수1을 받아 순위 items를 생성한다.
- 이미 있는 날짜는 영향 행 수0이므로 저장된 순위·generatedAt·집계 근거를 재사용한다.
- 동시에 처음 조회하면 날짜 기본 키의 INSERT 경합과 같은 트랜잭션 안에서 한 요청만 생성하고 다른 요청은 완료된 결과를 읽는다.
- KST 다음 날짜에 새 snapshot을 만든다. 초기 생성 실패 시 header와 items가 함께 rollback되어 다음 요청이 다시 생성할 수 있다.

API 경로·응답 필드·전일 집계 기준·공개/숨김 반영·프론트 호출을 유지한다. type/limit의 현재 main 처리 정책이나 전역 DB 오류 응답 형식을 일괄 변경하지 않는다. 새로운 migration이나 운영 DB 삭제/초기화가 필요하지 않다. 기존 순위 행을 지우거나 이미 배포된 migration을 수정하지 않는다.

## 검증 및 배포

NewsPopularitySnapshotRegressionTest는 운영과 분리된 localhost PostgreSQL에서 임의 스키마로 V0~V61을 적용하고 테스트 후 삭제한다.

1. 실제 SQL을 사용하는 MVC GET3회가 모두200이고 일별 header/items 수가 유지된다.
2. 별도 연결의 동시 최초 요청2개가 같은 결과를 받는다.
3. 서비스 재생성·같은 날 재조회는 generatedAt이 유지되고 KST 자정 이후 새 날짜를 생성한다.
4. 의도적으로 최초 items INSERT를 실패시킨 뒤 header/items rollback과 재시도 성공을 확인한다.

```powershell
.\gradlew.bat test --tests '*NewsPopularitySnapshotRegressionTest' bootJar
```

실행에는 VC_BE_TEST_POSTGRES_URL과 VC_BE_TEST_POSTGRES_USER를 격리 localhost 테스트 DB로 설정해야 한다. 지정하지 않으면 이 PostgreSQL 테스트는 조건부 제외된다. 운영 DB를 테스트 대상으로 사용하지 않는다. 2026-11-02 실제 PostgreSQL에서 수정 전4개 중3개 실패, 수정 후4개 모두 통과(실패/제외0)했다. production/test 전체 소스 컴파일과 bootJar도 성공했다. 전체 기존 테스트나 운영 배포 성공을 검증한 결과는 아니다.

백엔드 배포 후 동일 API를 연속 호출하여 모두200인지 확인하고 응답 generatedAt·순위가 같은 날 유지되는지 확인한다. 별도 프론트 수정은 필요하지 않다. 이 작업은 운영 SQL·배포를 실행하지 않았다.
