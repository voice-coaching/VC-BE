# Redis Stream AI Analysis Implementation Summary

## 작업 유형

- 외부 연동 구현
- 서비스 로직 작성
- 도메인 모델 보강
- 문서화

## 작업 개요

`docs/api/ai-redis-stream-contract.md`에 정의된 Backend-AI Redis Stream 계약을 백엔드 코드에 구현했다.

분석 요청 API 흐름에서 백엔드는 `analysis_results` row를 먼저 생성하고, DB transaction commit 이후 Redis Stream `analysis:request`에 분석 요청 메시지를 발행한다.

AI Worker가 Redis Stream `analysis:result`에 결과 메시지를 발행하면 백엔드 consumer가 이를 읽어 `analysis_results`, `analysis_segments`에 저장하고, DB 저장이 성공한 경우에만 ACK 처리한다.

## 신규 작성 파일

### `src/main/java/org/example/voice/training/domain/model/AnalysisJobRequestData.java`

- Redis Stream 분석 요청 메시지에 필요한 값을 담는 domain model을 추가했다.
- 포함 필드:
  - `analysisId`
  - `sessionId`
  - `recordingId`
  - `userId`
  - `audioUrl`
  - `scriptText`
  - `learningFocus`

### `src/main/java/org/example/voice/training/infrastructure/RedisAnalysisJobPublisher.java`

- `AnalysisJobPublisher`의 Redis Stream 구현체를 추가했다.
- `analysis.redis-stream.enabled=true`일 때 기본 활성화된다.
- `analysis:request` Stream에 계약 필드를 `XADD` 형태로 발행한다.
- Stream 이름은 `analysis.redis-stream.request-stream` 설정을 사용한다.

### `src/main/java/org/example/voice/analysis/domain/model/AnalysisResultStreamData.java`

- AI Worker가 발행한 분석 결과를 application 계층에서 사용하기 위한 domain model을 추가했다.
- 분석 결과 본문과 세그먼트 목록을 포함한다.
- Redis raw field DTO와 저장 유스케이스 사이의 내부 계약 역할을 한다.

### `src/main/java/org/example/voice/analysis/application/AnalysisResultStreamService.java`

- Redis Stream에서 수신한 분석 결과를 DB에 반영하는 application service를 추가했다.
- `PROCESSING`, `COMPLETED`, `FAILED` 상태별 저장 흐름을 담당한다.
- `COMPLETED` 결과 수신 시 분석 결과와 세그먼트를 저장한다.
- `FAILED` 결과 수신 시 분석 결과와 학습 세션 상태를 실패로 갱신한다.
- 이미 terminal 상태인 분석 결과에 늦게 도착한 메시지는 상태를 되돌리지 않고 무시한다.

### `src/main/java/org/example/voice/analysis/infrastructure/redis/AnalysisRedisStreamSchedulingConfig.java`

- Redis Stream consumer의 scheduling 활성화 설정을 추가했다.
- `analysis.redis-stream.enabled=true`일 때만 활성화된다.

### `src/main/java/org/example/voice/analysis/infrastructure/redis/AnalysisResultStreamMessageDto.java`

- Redis Stream `analysis:result` raw field를 파싱하기 위한 infrastructure 전용 DTO를 추가했다.
- 공개 API DTO와 분리했다.

### `src/main/java/org/example/voice/analysis/infrastructure/redis/AnalysisResultStreamConsumer.java`

- Redis Stream `analysis:result` consumer를 추가했다.
- consumer group으로 새 메시지를 읽고 처리한다.
- DB 저장 성공 후에만 ACK한다.
- 처리 실패 시 ACK하지 않아 pending entry로 남긴다.
- `ANALYSIS_RESULT_PENDING_TIMEOUT_MILLIS` 이후 pending 메시지를 claim해 재처리한다.
- `segments` field는 JSON array 문자열로 파싱한다.

## 수정 파일

### `src/main/java/org/example/voice/training/domain/port/AnalysisJobPublisher.java`

- 기존 `publish(Long analysisId, Long sessionId, Long recordingId)` 시그니처를 `publish(AnalysisJobRequestData request)`로 변경했다.
- Redis Stream 계약에 필요한 전체 payload를 port 경계에서 명시하도록 수정했다.

### `src/main/java/org/example/voice/training/application/TrainingAnalysisRequestService.java`

- 분석 요청과 재시도 흐름에서 `AnalysisJobRequestData`를 조회해 publisher에 전달하도록 수정했다.
- Redis 발행을 DB transaction commit 이후 수행하도록 `TransactionSynchronization.afterCommit()`을 적용했다.
- 커밋 전에 AI Worker가 메시지를 소비하거나, 롤백된 분석 요청의 고아 메시지가 생기는 문제를 줄였다.

### `src/main/java/org/example/voice/training/domain/port/VoiceRecordingReader.java`

- 분석 요청 메시지 생성을 위한 `findAnalysisJobRequest(...)` 조회 계약을 추가했다.

### `src/main/java/org/example/voice/training/infrastructure/VoiceRecordingReaderImpl.java`

- 선택 녹음, 학습 세션, 학습 콘텐츠 정보를 조합해 `AnalysisJobRequestData`를 반환하는 구현을 추가했다.
- Redis 요청 메시지의 `audioUrl`, `scriptText`, `learningFocus`, `userId`를 기존 DB entity에서 조회한다.

### `src/main/java/org/example/voice/training/infrastructure/MockAnalysisJobPublisher.java`

- 변경된 `AnalysisJobPublisher` 시그니처에 맞게 수정했다.
- `analysis.redis-stream.enabled=false`일 때만 활성화되도록 조건을 추가했다.
- 테스트 환경에서는 Redis Stream을 비활성화해 mock publisher를 사용한다.

### `src/main/java/org/example/voice/analysis/domain/entity/AnalysisResult.java`

- AI Worker 결과 반영을 위한 도메인 메서드를 추가했다.
- 추가 메서드:
  - `markProcessing()`
  - `complete(...)`
  - `fail(String failureReason)`
  - `canStartProcessing()`
  - `canFinish()`
- 허용 상태 전이만 반영할 수 있도록 상태 검사 메서드를 추가했다.

### `src/main/java/org/example/voice/analysis/domain/entity/AnalysisSegment.java`

- AI Worker 세그먼트 결과 저장을 위한 정적 팩토리 메서드 `create(...)`를 추가했다.
- segment entity 생성을 외부에서 필드 직접 접근 없이 수행하도록 했다.

### `src/main/java/org/example/voice/analysis/domain/port/AnalysisResultWriter.java`

- Redis Stream 결과 처리 시 pessimistic write lock으로 분석 결과를 조회하기 위한 `findByIdForUpdate(Long analysisId)` 계약을 추가했다.

### `src/main/java/org/example/voice/analysis/domain/port/AnalysisSegmentWriter.java`

- 분석 세그먼트 교체 저장을 위한 `replaceSegments(...)` 계약을 추가했다.

### `src/main/java/org/example/voice/analysis/infrastructure/AnalysisResultWriterImpl.java`

- `findByIdForUpdate(...)` 구현을 추가했다.
- 분석 결과 저장 시 분석 상세, 세션 기준 분석 결과, 세그먼트 캐시를 무효화하도록 했다.

### `src/main/java/org/example/voice/analysis/infrastructure/AnalysisSegmentWriterImpl.java`

- 기존 빈 구현체에 세그먼트 교체 저장 로직을 추가했다.
- 기존 분석 세그먼트를 삭제한 뒤 새 세그먼트 목록을 저장한다.

### `src/main/java/org/example/voice/analysis/infrastructure/AnalysisSegmentJpaRepository.java`

- 세그먼트 교체 저장을 위해 `deleteByAnalysisResultId(Long analysisId)`를 추가했다.

### `src/main/java/org/example/voice/training/infrastructure/AnalysisResultJpaRepository.java`

- 분석 결과 row를 write lock으로 조회하기 위한 `findByIdForUpdate(...)` 쿼리를 추가했다.

### `src/main/resources/application.yaml`

- Redis Stream 설정을 추가했다.
- 추가 설정:
  - `analysis.redis-stream.enabled`
  - `analysis.redis-stream.request-stream`
  - `analysis.redis-stream.result-stream`
  - `analysis.redis-stream.backend-consumer-group`
  - `analysis.redis-stream.backend-consumer-name`
  - `analysis.redis-stream.poll-count`
  - `analysis.redis-stream.poll-delay-millis`
  - `analysis.redis-stream.pending-timeout-millis`

### `src/test/resources/application.yaml`

- 테스트 환경에서 `analysis.redis-stream.enabled=false`를 설정했다.
- 테스트 실행 시 Redis Stream consumer/publisher가 실제 Redis에 연결되지 않도록 했다.

## 수정 문서

### `docs/api/ai-redis-stream-contract.md`

- 백엔드 구현 위치를 추가했다.
- Redis Stream 관련 환경 변수를 추가했다.
- 요청/결과 메시지 예시를 실제 Redis Stream `XADD` 형식에 맞게 수정했다.
- `segments`가 Redis Stream field 하나에 JSON array 문자열로 들어간다는 점을 명시했다.
- DB transaction commit 이후 request stream에 발행한다는 점을 명시했다.
- pending result 메시지 claim 및 재처리 정책을 실제 구현과 맞췄다.
- terminal 상태에 늦게 도착한 stale 메시지는 상태를 되돌리지 않고 무시한다고 명시했다.

### `docs/architecture/deployment.md`

- Redis Stream 운영 설정과 환경 변수를 추가했다.
- request/result stream, consumer group, backend publisher/consumer 위치를 문서화했다.
- 처리 실패 메시지는 ACK하지 않고 pending entry로 남기며, idle timeout 이후 claim해 재처리한다고 설명했다.

### `docs/architecture/directory.md`

- Redis Stream 관련 infrastructure 디렉토리 구조를 추가했다.
- `analysis/infrastructure/redis`와 `training/infrastructure/RedisAnalysisJobPublisher`의 책임을 문서화했다.

### `docs/architecture/flow.md`

- 분석 요청 및 결과 조회 흐름을 Redis Stream 기반으로 갱신했다.
- DB commit 이후 request stream 발행, result stream 수신, ACK, pending 재처리 단계를 반영했다.

## 구현 후 주요 흐름

```text
Client
-> POST /api/training-sessions/{sessionId}/analyze
-> TrainingAnalysisRequestService
-> analysis_results PENDING row 생성
-> transaction commit
-> RedisAnalysisJobPublisher
-> Redis Stream analysis:request
-> AI Worker
-> Redis Stream analysis:result
-> AnalysisResultStreamConsumer
-> AnalysisResultStreamService
-> analysis_results / analysis_segments 저장
-> XACK analysis:result
```

## 정합성 보정 사항

### DB commit 이후 Redis 발행

분석 요청 메시지는 DB transaction이 성공적으로 commit된 뒤에만 발행한다.

이렇게 해서 Redis 메시지는 있는데 DB row가 없는 상황을 줄였다.

### ACK 정책

`analysis:result` 메시지는 DB 저장이 성공한 뒤에만 ACK한다.

파싱 실패, DB 저장 실패, 기타 예외가 발생하면 ACK하지 않는다.

### Pending 재처리

ACK되지 않은 `analysis:result` pending 메시지는 `analysis.redis-stream.pending-timeout-millis` 이후 consumer가 claim해 재처리한다.

기본값은 300000ms, 즉 5분이다.

### 상태 전이 방어

백엔드는 다음 상태 전이만 반영한다.

```text
PENDING -> PROCESSING
PENDING -> COMPLETED
PROCESSING -> COMPLETED
PENDING -> FAILED
PROCESSING -> FAILED
```

이미 `COMPLETED` 또는 `FAILED`인 분석 결과에 늦게 도착한 메시지는 상태를 되돌리지 않고 무시한다.

## 검증 결과

### 성공

```bash
./gradlew.bat compileJava
./gradlew.bat compileTestJava
```

운영 코드와 테스트 코드 컴파일은 성공했다.

### 실패

```bash
./gradlew.bat test
```

전체 테스트 실행은 실패했다.

실패 양상은 모든 테스트 클래스가 `ClassNotFoundException`으로 로드되지 않는 형태였다. 테스트 `.class` 파일은 `build/classes/java/test`에 생성되어 있었으므로, 이번 Redis Stream 구현 코드의 컴파일 오류라기보다 Gradle test runtime 또는 현재 Windows 경로/테스트 실행 환경 문제로 보인다.

## 남은 확인 사항

- 실제 Redis 서버와 AI Worker를 연결한 통합 검증이 필요하다.
- AI Worker는 `analysis:result`의 `segments` 값을 JSON array 문자열 field로 발행해야 한다.
- 운영 환경 `.env`에 Redis Stream 관련 환경 변수를 추가해야 한다.
- retry exceeded 정책은 별도 운영 worker 또는 후속 이슈에서 구현할 수 있다.
