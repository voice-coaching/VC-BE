# 프론트 전달: 클래스 녹음의 기존 직접 분석 경로 연결

## 작업 유형

- 클래스 연습의 분석 화면 선택 분기 정정
- 기존 개별 녹음·직접 AI 분석 기능 재사용
- 프론트 변경사항 전달 문서 작성

## 1. 수정 범위

현재 사용하는 직접 분석 경로로 클래스 녹음을 연결한다. 새 연습 묶음, 묶음 ID, 테이블, 분석 API를 만들지 않는다. 승급 횟수·승급 채점·회원 이용등급은 이번 범위에 포함하지 않는다. 프론트 코드는 이 문서 작업에서 수정하지 않았다.

이 PR은 전달 문서만 추가한다. 현재 운영 방향에 대한 사용자 설명은 프론트↔AI 직접 분석이며, 이 문서의 전환 지침도 그 방향을 기준으로 한다. 앞서 임시 작성했던 백엔드 Canonical 허용 수정은 되돌렸으며 이 PR에 포함하지 않는다.

### 최신 develop 대조

- 이 문서의 rebase 기준은 `e38b9b5`다.
- 선행 커밋 `3821415`에서 백엔드가 이미 고정된 발음 클래스 음성을 허용하도록 변경되었다. capability의 COURSE.supported도 true다. 따라서 최신 소스 자체가 여전히 COURSE=false라고 설명하면 안 된다.
- 최신 백엔드는 V6 계약 및 문장별 원문 고정도 포함한다. 코드 이름에 runpod/Canonical이 남아 있다는 사실만으로 현재 물리적인 AI 운영 환경이나 해당 경로의 폐기를 단정하지 않는다.
- 사용자가 제시한 COURSE=false 응답은 당시 관찰한 배포 응답이다. 최신 소스와 다르면 실제 배포 리비전·연결 환경을 먼저 대조한다. 이 PR은 운영 반영이나 실제 분석 성공을 확인한 기록이 아니다.

## 2. 당시 증상과 확인한 프론트 분기

프론트 코드 확인 기준: main의 `45a9faa`. 이후 프론트 변경이 있다면 아래 분기를 다시 대조한다.

`src/components/practice-session.tsx`에서 다음 조건을 모두 만족할 때만 `DirectPracticeSession`을 선택한다.

```tsx
directAnalysisEnabled &&
!params.get("courseId") &&
!params.get("sessionId") &&
!params.get("courseStepId")
```

따라서 다음과 같은 클래스 URL은 `courseId`/`courseStepId` 때문에 직접 분석 경로에서 제외된다.

```text
/practice/274?courseId=60&courseStepId=12&exampleId=pronunciation-advanced-r1-1&exampleRevision=1
```

그 결과 `RecordingPracticeSession`으로 진입해 `/api/analysis-capabilities/canonical`을 확인한다. 사용자가 제공한 당시 응답이 COURSE=false였으므로 해당 시점에는 COURSE_ANALYSIS_UNSUPPORTED로 차단됐다. 예문 목록 조회와 분석 지원 여부 조회는 별개다. 최신 develop에는 클래스 지원 변경이 있으므로 현재도 같은 응답인지는 배포 후 재확인이 필요하다.

현재 운영 방침대로 클래스를 직접 분석에 연결하려면 아래 프론트 전환을 적용한다. 반대로 백엔드 세션 분석 경로를 유지하기로 했다면 아래 전환을 무조건 적용하지 말고 최신 백엔드 배포와 계약을 먼저 맞춘다. 두 흐름을 혼합해 같은 녹음을 중복 제출하지 않는다.

## 3. 프론트에서 고칠 부분

| 파일 | 필요한 수정 |
| --- | --- |
| `src/components/practice-session.tsx` | 신규 클래스 연습이라는 이유만으로 직접 분석에서 제외하지 않는다. 검증된 클래스 예문도 직접 분석 화면으로 연결한다. 승급시험과 기존 sessionId 재개 분기는 일괄 변경하지 않는다. |
| `src/components/direct-practice-session.tsx` | 현재 예문 정보와 순서를 받을 수 있도록 연결한다. 화면에서 선택한 문장과 직접 분석에 전송하는 scriptText/contentId가 같아야 한다. |
| `src/lib/practice-examples.ts` | 기존 `loadPracticeSelection()`의 클래스·단계·개정·예문·콘텐츠 원문 검증을 유지한다. |
| `src/lib/class-practice.ts` | 기존 예문 정렬과 `classPracticeHref()` URL 생성 기능을 재사용한다. |
| `src/lib/direct-analysis.ts` | 기존 `submitDirect`, `findDirect`, `readDirect`, `observeDirect`, `linkDirectHistory`를 재사용한다. 새 HTTP 계약을 임의로 만들지 않는다. |

분기에서 조건 두 개를 지우는 것만으로 전체 흐름이 완성되지는 않는다. `DirectPracticeSession`이 클래스 예문/순서를 받고 다음 문장으로 넘어갈 수 있게 연결해야 한다. `courseId`, `courseStepId`, `exampleId`, `exampleRevision`을 URL에서 제거해 일반 연습으로 위장하지 않는다.

## 4. 기존 API 사용 방식

### 클래스 예문

```http
GET /api/courses/{courseId}/practice-examples
Authorization: Bearer <accessToken>
```

`data.items`를 order 오름차순으로 사용한다. 전체 문장 수는 items.length이고 stepCount가 아니다. 예문마다 새로운 콘텐츠나 백엔드 학습 세션을 임의 생성하지 않는다.

### 직접 분석

현재 프론트의 분석 origin 설정은 `NEXT_PUBLIC_DIRECT_ANALYSIS_ORIGIN`이며 코드 기본값은 다음과 같다. 실제 배포에 별도 환경변수가 있으면 그 설정이 우선한다.

```text
https://ai.voice-coaching.site/direct
```

기존 `submitDirect()`가 호출하는 경로:

```http
POST {DIRECT_ANALYSIS_ORIGIN}/api/v1/analyses
```

기존 음성 변환 및 metadata 인코딩을 재사용한다. metadata에는 scriptText, contentId, clientAttemptId, historyClaimSha256가 포함된다. AI 요청에 백엔드 사용자 토큰·쿠키·외부 서비스 비밀키를 추가하지 않는다.

작업 조회 및 이벤트 수신:

```http
GET {DIRECT_ANALYSIS_ORIGIN}/api/v1/analyses/{jobId}
GET {DIRECT_ANALYSIS_ORIGIN}/api/v1/analyses/{jobId}/events
```

jobId/clientAttemptId/historyClaim은 현재 문장의 기존 분석 시도에 연결한다. 재연결 때문에 무조건 새 작업을 만들지 않는다. 기존 결과 schema와 실행 식별 검증도 유지한다.

### 회원 이력 연결

```http
POST /api/direct-analysis-history/{jobId}/link
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "historyClaim": "<현재 분석 시도의 historyClaim>"
}
```

AI가 백엔드에 전달하는 결과 이력과 회원 연결은 기존 계약을 사용한다. 프론트가 받은 점수 JSON을 임의의 백엔드 주소에 다시 POST하거나, jobId를 training session ID처럼 사용하지 않는다. 이력 기능의 실제 활성화·저장 상태는 별도로 확인해야 한다.

## 5. 문장별 전송과 마지막 결과 화면

요구사항은 문장마다 녹음을 별도로 전송하고 마지막에 모든 문장의 평가를 모아서 보여주는 흐름이다.

```text
문장 1 녹음 → 기존 직접 분석에 개별 접수
문장 2 녹음 → 기존 직접 분석에 개별 접수
…
마지막 문장 접수
→ 각 jobId의 결과 확인
→ 문장 순서대로 전체 평가 화면 표시
```

각 문장의 exampleId/order와 해당 jobId를 프론트에서 구분한다. 페이지 전환으로 이전 작업의 식별자·결과를 잃거나 다른 문장 결과를 덮어쓰지 않도록 한다. 회원이 바뀌면 이전 회원의 복구 상태를 재사용하지 않는다. 재시도는 해당 문장의 작업을 대상으로 한다.

현재 확인한 직접 분석 클라이언트는 작업별 결과를 조회한다. 따라서 **개별 결과를 프론트에서 모아 마지막에 표시하는 것과 서버가 한 HTTP 응답으로 일괄 반환하는 것은 다르다.** 이번 수정에서 백엔드 일괄 결과 API를 새로 만든 것으로 안내하지 않는다. AI 측 일괄 반환을 사용하려면 이미 제공되는 실제 계약이 있는지 별도 확인해야 한다.

## 6. 오류 처리 및 검증

| 상황 | 처리 |
| --- | --- |
| 예문 조회 실패 또는 개정 불일치 | 녹음을 시작하지 않고 클래스에서 예문을 다시 선택하도록 안내 |
| 개별 녹음 접수 실패 | 현재 문장 유지. 접수되지 않은 문장을 완료로 계산하지 않음 |
| 결과 연결 일시 중단 | 기존 jobId 조회로 복구. 중복 분석을 무조건 제출하지 않음 |
| 일부 문장 결과가 아직 없음 | 준비 중인 문장을 표시하고 전체 결과가 완료됐다고 안내하지 않음 |
| 문장 분석 실패 | 실패한 문장과 재시도를 구분. 다른 문장의 결과는 보존 |
| 이력 연결 충돌/지연 | 분석 결과 수신과 구분해 상태 안내 |
| report-saves 오류 | 별도 리포트 저장 선택 기능의 문제. 클래스 녹음 실패와 혼합하지 않음 |

배포 후 클래스 60에서 확인할 항목:

1. 예문 API가 반환한 콘텐츠 ID와 표시 원문이 일치한다.
2. 신규 클래스 연습이 `DirectPracticeSession`으로 진입한다.
3. 직접 분석으로 전환한 신규 클래스 연습이 백엔드 세션 분석과 중복 제출되지 않고 기존 직접 분석 요청을 수행한다.
4. 각 문장의 녹음이 각각 접수되고 jobId가 구분된다.
5. 마지막에 문장 순서대로 결과를 표시하며, 누락/실패를 정상 완료로 표시하지 않는다.
6. 직접 분석 이력 연결 상태를 확인한다. 클래스 진도·승급 횟수가 자동으로 갱신된다고 가정하지 않는다.

## 7. 반영 상태

- 백엔드: 이 PR은 기능·스키마·API를 추가하지 않음. 선행 develop의 클래스 음성 허용·문장 원문 고정 변경은 그대로 포함된 기준에서 작성함.
- 프론트: 코드 수정 없이 위 변경사항을 전달함. 실제 수정 및 배포 후 확인이 필요함.
- 별도 브랜치: 백엔드의 `docs/class-direct-analysis-handoff`에서 전달 문서만 커밋함. 코드 수정·배포 PR이 아님.
- 승급 집계·채점: 변경 없음.
