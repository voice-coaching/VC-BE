# 프론트 전달: 클래스 문장 분석 연결의 최신 검증 결과

## 작업 유형

- 클래스 Canonical 차단 해소 커밋 검증
- 최신 프론트 문장별 녹음·분석·결과 표시 경로 대조
- 과거 직접 분석 전환 안내 정정

## 1. 결론

**기존 COURSE_ANALYSIS_UNSUPPORTED를 유발한 백엔드의 클래스 제외 조건은 3821415에서 수정되었다.** 최신 프론트도 개별 문장 녹음과 분석 결과를 모아서 표시하는 세션 기반 V6 경로로 변경되었다. 앞서 제안한 DirectPracticeSession으로의 전환은 현재 코드에 그대로 적용하지 않는다.

이 문서는 소스·회귀 테스트·배포 진단 검증이다. 인증된 실제 회원으로 클래스 60의 녹음→분석→결과 확인을 완료한 실사용 검증은 아니다.

## 2. 확인한 리비전

| 대상 | 리비전 | 확인 내용 |
| --- | --- | --- |
| 백엔드 develop | e38b9b5 | 클래스 지원 및 문장별 원문 고정 병합 |
| 핵심 백엔드 커밋 | 3821415 | COURSE.supported=true, 교육 개정이 고정된 발음 클래스 음성 허용 |
| 백엔드 운영 배포 커밋 | c0d9d77b525eeb8604306299901779d04dcebe83 | GitHub 비교에서 3821415를 포함하는 후속 커밋임을 확인 |
| 프론트 main | 6c0fb91 | 신규 연습을 SentencePracticeSequence로 연결하고 V6 계약 사용 |
| 과거 프론트 확인 기준 | 45a9faa | courseId/courseStepId가 있으면 DirectPracticeSession에서 제외되던 코드. 현재 안내의 기준으로 사용하지 않음 |

운영 배포 근거: [GitHub Actions 41294521775](https://github.com/voice-coaching/VC-BE/actions/runs/41294521775). 코드 이름의 runpod/Canonical만으로 실제 AI 서버의 물리적 호스팅 방식 또는 현재 경로의 폐기를 판단하지 않는다.

## 3. 백엔드에서 해소된 조건

### 지원 여부

`CanonicalCapabilitiesService`에서 다음처럼 변경되었다.

```text
이전: COURSE.supported=false, reasonCode=COURSE_ANALYSIS_UNSUPPORTED
변경: COURSE.supported=true, reasonCode=null
```

### 실제 분석 허용 범위

`CanonicalRequestScope`는 courseStepId와 courseEducationRevisionId가 고정된 음성 클래스 세션을 허용한다. PRONUNCIATION 학습 초점, 품질 PASS, 음성/영상 조건 등 기존 제한은 유지한다. 억양 전용·BOTH·영상 클래스를 전부 지원한다는 의미는 아니다.

허용 검사가 null 유무를 확인하는 부분만으로 구성된 것처럼 사용해서는 안 된다. 정상 세션 생성 과정의 게시 콘텐츠·클래스/예문 연결·교육 개정 검증을 통과해 생성된 세션을 사용해야 한다.

### 문장별 원문 고정

같은 콘텐츠 안에서도 녹음한 문장만 분석할 수 있도록 세션 생성 입력과 저장 구조에 sentenceIndex/sentenceText 및 원문 고정 검증이 추가되었다. 임의의 클라이언트 문장을 그대로 신뢰하지 않고 원본과 대조한다. 세부 계약은 최신 TrainingSessionCreateRequestDto와 SentenceTranscript 구현을 따른다.

## 4. 실제 배포 진단

위 배포의 2026-11-01 18:50:12 KST 배포 후 진단 결과:

```json
{
  "backendReadiness": {
    "admissionEnabled": true,
    "supported": true
  },
  "workerHealth": "READY",
  "reasonCodes": [],
  "workerDetails": {
    "admissionEnabled": true,
    "audioDigestsMatch": true,
    "missingAudioSchemas": []
  }
}
```

과거의 admissionEnabled=false와 음성 계약 누락은 이 배포 후 진단 시점에는 재현되지 않았다. 서버 상태는 변할 수 있으므로 위 결과를 영구적인 정상 상태로 해석하지 않는다. 실제 회원 세션·녹음별 분석 성공도 이 진단만으로 보장되지 않는다.

## 5. 최신 프론트 흐름

`src/components/practice-session.tsx`의 최신 흐름:

```text
승급시험 URL → TitleExamSession
신규 연습이며 기존 sessionId가 없음 → SentencePracticeSequence
기존 sessionId 재개 또는 별도 localOnly 흐름 → RecordingPracticeSession
```

`SentencePracticeSequence`는 클래스 예문을 문장 목록으로 구성하고, 문장별 분석 접수·결과 추적 및 결과 화면을 관리한다. 백엔드에 별도 연습 묶음 API를 새로 추가하는 방식은 아니다.

`src/lib/api/analysis-capabilities.ts`는 AUDIOVISUAL_PROFILE을 확인하고, `src/lib/api/canonical.ts`의 신규 분석 결과 계약은 V6다. V5는 과거 결과 reader 계약으로 남는다. 코드에 예전 상수가 존재한다는 이유만으로 현재 요청이 V5라고 판단하지 않는다.

이전 문서의 다음 안내는 현재 신규 연습 흐름에 적용하지 않는다.

- 클래스 파라미터를 직접 분석 제외 조건에서 제거하라는 안내
- DirectPracticeSession으로 클래스 화면을 전환하라는 안내
- 신규 클래스 분석을 /direct/api/v1/analyses로 보내라는 안내

현재 세션 기반 경로와 과거 직접 분석 경로를 혼합해 같은 녹음을 중복 제출하지 않는다. 프론트·백엔드는 같은 V6 계약으로 배포되어야 한다.

## 6. 재실행한 테스트와 한계

다음 테스트를 최신 develop 기반 코드에서 재실행했다.

- CanonicalTitleScoreTest: 클래스 음성 허용, 억양 초점 거부, capability 지원과 readiness 분리 등
- SentenceSessionIntegrationTest: 개별 문장 원문 고정, 원문 변조 거부, 선택 문장 분석 입력, 원문 변경 검증 등
- UnifiedV6PublisherTest: V6 분석 발행 관련 검증

결과: **15개 통과, 실패 0개, 제외 0개**. 전체 회귀 테스트나 외부 AI의 실추론을 이번 검증에서 실행한 것은 아니다. 클래스 scope 테스트는 mock 세션 조건 검증이며, 이것만으로 전체 운영 흐름 검증이 끝났다고 보지 않는다.

## 7. 실제 화면에서 남은 확인

1. 배포 프론트가 최신 코드인지 확인한다. 저장소 main의 변경만으로 Vercel 배포 반영을 단정하지 않는다.
2. 로그인 후 클래스 60에서 예문 5개와 현재 콘텐츠/예문 ID가 일치하는지 확인한다.
3. capability 응답의 COURSE.supported=true, admissionEnabled=true와 V6 결과 계약을 확인한다.
4. 실제 문장 녹음이 세션 기반 분석으로 접수되는지 확인한다.
5. 문장별 결과가 올바른 원문과 연결되고 마지막 화면에서 순서대로 표시되는지 확인한다.
6. 일부 실패·연결 중단을 전체 완료로 표시하지 않고 재조회/재시도할 수 있는지 확인한다.
7. report-saves 등 별도 기능의 오류가 발생하면 분석 접수 문제와 구분한다.

## 8. 반영 상태

- 백엔드 클래스 지원 변경은 선행 develop에 이미 존재하며, 해당 커밋을 포함한 운영 배포 성공 및 배포 후 readiness를 확인했다.
- 최신 프론트 main은 세션 기반 문장 순차 분석을 사용한다. 실제 Vercel 반영 및 회원 녹음 검증은 별도다.
- 이 PR은 최신 확인 내용에 맞춘 문서 정정만 포함한다. 프론트·백엔드 코드, DB 스키마, 별도 분석 API를 변경하지 않는다.
- 승급 집계·채점은 이번 작업에서 수정하지 않는다.
