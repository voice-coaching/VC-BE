# 독립 음성 분석 제출·결과 수집

기준일 2026-11-07. 사용자 제공 2차 handoff와 [API v1](../api/independent-analysis-v1.md)에 따른 Backend 소스 구현이다.
작업 브랜치는 `feat/independent-analysis-pull-20261107`, 기준 소스는 `main`의 `8e518f4`다.
운영 배포·DB migration 적용·AI 프로필 전환·프런트 종단 검증은 수행하지 않았다. 기본 플래그는 모두 false다.

## 책임과 transaction

기존 사용자 인증·소유 녹음 조회를 유지한다. independent 선택 시 요청 서비스는 사용자 행,
세션 행 순서로 잠그고 취소/소유권·진행 중 분석·상품 이용 기간/잔여량·원고/음성 한도를 검사한다.
분석 시도, 이용량 예약, 제출 outbox는 한 DB transaction에 저장하며 이 구간에 AI HTTP나 S3 다운로드가 없다.
`clientRequestId`는 Backend request event UUID와 같고, Backend 실행 UUID와 AI job/execution UUID는 별도 열이다.

worker는 SUBMIT/COLLECT/ACK별 단일 실행 lane을 갖고 DB에서 due 행만 `FOR UPDATE SKIP LOCKED`로 가져온다.
행의 120초 claim은 Backend worker 중복 실행 보호이며 AI lease가 아니다.
저장 시 같은 claim과 만료 시각을 확인한다. 분석 갱신에는 현재 request/execution/profile·소유권·선택 녹음·삭제/취소 여부를 다시 확인한다.
HTTP와 원음 읽기는 짧은 DB transaction 밖에서 수행한다. 작업별 동시성은 인스턴스당 lane별 1개다.

SUBMIT은 먼저 원래 UUID를 조회한다. 미존재이면 저장된 S3 PCM 객체의 크기/SHA·WAV 형식을 확인한 뒤
같은 UUID·원고·bytes로 POST한다. 허용된 recording bucket/prefix만 읽고 URL이나 AWS credential을 AI에 보내지 않는다.
COLLECT는 상태를 확인하고 원본 결과의 digest/작업/미디어 identity를 대조한 뒤 bytes를 먼저 보관한다.
결과 adapter 오류가 나도 AI 완료 상태와 원본은 보존된다. 공개 view로 변환할 수 있을 때 분석 결과와 이용량 정산을 commit한다.
ACK lane은 공개 분석 캐시를 비우고 commit된 digest로만 수령 확인한다.
실패 결과가 없는 FAILED/CANCELLED는 null 점수로 commit하고 로컬 후처리만 마친다. digest 없이 ACK하지 않는다.

현재 시도가 아닌 옛 결과는 해당 시도의 outbox에만 남고 현재 분석을 덮지 않는다. 제출 전 취소는 종료하고 예약을 해제한다.
이미 AI에 접수된 비활성 시도는 취소를 요청한 후 종료 상태/기존 결과를 수집한다.
기존 legacy callback·ingestion은 독립 실행을 갱신하지 못한다. 독립 실행에는 legacy deadline/claim을 설정하지 않는다.

## 상태와 재개

| DB stage | 의미·다음 처리 |
| --- | --- |
| SUBMIT_PENDING | DB 접수 완료, UUID 조회 후 제출 |
| LOOKUP_PENDING | POST 전 기록 또는 접수 응답 미확인; UUID 조정부터 반복 |
| COLLECTING | AI job/execution 연결 저장, QUEUED/RUNNING 상태 조회 |
| COLLECTING_FAILED | HTTP·digest·adapter 수집 장애; 동일 job/result 재조회 |
| ACK_PENDING | 결과/실패 commit 완료; 캐시 정리 후 ACK 또는 결과 없는 로컬 종료 |
| ACKED | ACK 수령 확인 완료 |
| SUBMISSION_REJECTED | 수정 없는 반복 제출을 금지한 입력 오류/409 |
| CANCELLED | AI 미접수 상태의 비활성 시도 종료 |
| FINISHED | 원본 결과 없는 AI 종료 상태의 로컬 후처리 완료 |

AI status는 QUEUED/RUNNING/COMPLETED/FAILED/CANCELLED로 별도 저장한다.
COLLECTING_FAILED를 AI FAILED로 덮지 않는다. 원래 UUID나 AI job을 새 추론/GPT 요청으로 바꾸는 복구는 없다.
클레임 만료·Backend 재시작 뒤 due 행부터 재개한다. 상태/결과/ACK 장애는 같은 행에서 대기한다.
자동 backoff는 2초부터 최대 256초(계산 상한 300초)에 0~3초 jitter를 더한다.
Retry-After는 최대 24시간까지 반영하며 401/403은 최소 300초다.
일시 장애의 전체 대기 기간/횟수 상한으로 결과를 버리지 않는다. 입력 오류는 반복을 중단한다.

## 공개 응답과 모델 경계

기존 `/api` endpoint, 사용자 로그인, Backend 분석/녹음 정수 ID를 유지한다.
독립 transport DTO에서 기존 canonical 공개 view를 만들며 legacy CanonicalHandoffDocument를 파싱하지 않는다.
점수와 총평은 AI 값을 그대로 전달하고 숫자를 재계산하지 않는다.
원본은 source identity 검증을 거치지만 비공개 근거·CORE 전체 구조·모델 release/hash는 접수/수집 gate가 아니다.
미지의 추가 필드는 원본 bytes에 보관되며 사용하지 않아도 수집을 막지 않는다.
영상은 접수하지 않으며 기존 공개 view의 영상 필드는 NOT_PROVIDED/null 점수로 표현한다.
피드백 재생성은 독립 결과에서 비활성이다. 실패와 null 점수는 가짜 성공으로 보정하지 않는다.
기존 title 점수 참조도 독립 commit된 실제 점수에 연결한다.

## 설정

| 환경변수 | 기본값·의미 |
| --- | --- |
| ANALYSIS_INDEPENDENT_SUBMISSION_ENABLED | false. 새 음성 접수를 독립 경로로 선택 |
| ANALYSIS_INDEPENDENT_WORKERS_ENABLED | false. 제출·수집·ACK worker 실행 |
| AI_INDEPENDENT_ENDPOINT_URL | 없음. 경로 없는 HTTPS AI origin |
| AI_INDEPENDENT_ALLOWED_ORIGINS | 없음. 정확한 origin 허용 목록(쉼표 구분) |
| AI_ANALYSIS_API_TOKEN | 없음. 기존 별도 서버 토큰, 32~512자 |
| AI_INDEPENDENT_HTTP_TIMEOUT | PT25S. 전체 HTTP 응답 timeout, 허용 1~60초 |

HTTP connect timeout은 5초, redirect는 금지다. 상태 본문은 128 KiB, 결과는 2 MiB로 제한한다.
원음 S3 다운로드는 기존 recordingS3Client와 bucket 설정을 사용하고 API timeout 20초를 둔다.
설정 누락은 새 경로 접수를 닫지만 Backend 기동/liveness를 실패시키지 않는다.
새 접수 플래그를 끄고 worker만 켜 기존 독립 작업의 수집을 계속할 수 있다.
반대로 플래그가 false인 기본 상태에서는 legacy 정책을 유지한다. 전체 서비스의 legacy 설정을 이 변경으로 지우지 않는다.

## DB와 보존

`V68__independent_analysis_pull.sql`은 기존 V67 다음에 새 outbox 테이블을 추가한다.
테이블 명세는 [DB schema](../database/schema.md)의 독립 분석 항목을 참조한다.
raw result는 최대 2 MiB의 BYTEA와 SHA로 보존하며 ACK 뒤 자동 삭제하지 않는다.
원고는 기존 PrivateTextCipher port를 거쳐 저장한다. 이 작업이 기존 cipher 구현의 암호화 강도를 변경하거나 보장하지 않는다.
S3 객체 자체는 기존 보존/삭제 정책을 따른다. 영구 장애·spool 용량·오래된 수집 대기는 별도 운영 관측 대상이다.

## 단계적 전환과 검증 범위

1. Backend V68과 소스를 플래그 false로 배포하고 기존 pending legacy 작업을 구분한다.
2. AI 후보 소스·실제 CORE/H5·영구 spool/백업·분리 scratch를 AI 운영자가 준비한다.
3. 기존 작업을 drain하고 journal·미인수 원본을 보존한다. 169/170을 포함한 옛 결과는 이 구현에서 재실행/변환하지 않는다.
4. AI 로컬 운영 통제로 독립 프로필을 전환하고 executor/store/admission을 확인한다.
5. Backend에 서버 인증·origin을 설정하고 worker와 새 접수를 단계적으로 켠다. 전환 중 요청은 DB outbox에 남는다.
6. 별도 승인된 시험으로 접수→AI 독립 완료→raw GET→Backend commit→ACK→기존 화면을 확인한다.

검증 완료: Java 21의 `compileJava compileTestJava bootJar -x test`, 정적 diff 검토.
자동 테스트·새 fixture·실제 녹음/추론/GPT·브라우저 자동화는 수행하지 않았다.
DB migration 적용, HTTP 오류/재시작/중복 접수/ACK 유실의 실제 환경 검증, 운영 종단 검증은 남아 있다.
빌드 성공을 운영 전환이나 위 완료 조건의 실증으로 보고하지 않는다.
