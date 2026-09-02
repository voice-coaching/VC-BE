# 상태 문서 - voice

이 문서는 voice 백엔드의 주요 상태 모델, enum 값, 상태 전이 규칙을 기록한다.
상태를 추가하거나 변경할 때는 관련 entity, API 문서, DB schema, 테스트를 함께 검토한다.

## 상태 작성 기준

- 상태 값은 domain `type` enum을 source of truth로 둔다.
- 저장되는 상태는 JPA entity 필드와 DB column에 반영한다.
- API 응답에서는 enum 값을 문자열로 반환한다.
- terminal 상태는 더 이상 일반 흐름으로 진행되지 않는 최종 상태를 의미한다.
- 상태 전이는 application service 또는 domain entity method를 통해 명시적으로 수행한다.

## 상태 종류

| State | Owner | Source of Truth | Persisted | Notes |
| --- | --- | --- | --- | --- |
| 사용자 상태 | `user` | `UserStatus` | `users.status` | 계정 사용 가능 여부를 나타낸다. |
| 사용자 역할 | `user` | `UserRole` | `users.role` | 사용자 권한 수준을 나타낸다. |
| OAuth 제공자 | `auth` | `OAuthProvider` | `social_accounts.provider` | 소셜 로그인 provider를 나타낸다. |
| 온보딩 현재 수준 | `onboarding` | `CurrentLevel` | `onboarding_profiles.current_level` | 사용자의 현재 학습 수준이다. |
| 콘텐츠 게시 상태 | `practicecontent`, `course` | `PublishStatus` | `practice_contents.status`, `courses.status` | 공개 조회 가능 여부를 결정한다. |
| 콘텐츠 유형 | `practicecontent` | `ContentType` | `practice_contents.content_type` | 학습 콘텐츠의 유형이다. |
| 학습 난이도 | `practicecontent`, `course` | `Difficulty` | `practice_contents.difficulty`, `courses.difficulty` | 콘텐츠와 클래스 난이도이다. |
| 학습 초점 | `practicecontent`, `training` | `LearningFocus` | `practice_contents.learning_focus`, `training_sessions.learning_focus` | 발음/억양 학습 초점이다. |
| 기준 음성 화자 | `practicecontent` | `SpeakerType` | `reference_audios.speaker_type` | 기준 음성의 출처 또는 화자 유형이다. |
| 학습 세션 상태 | `training` | `TrainingSessionStatus` | `training_sessions.status` | 학습 세션 생명주기이다. |
| 녹음 품질 상태 | `training` | `RecordingQualityStatus` | `voice_recordings.quality_status` | 녹음 파일의 분석 가능 여부이다. |
| 분석 상태 | `analysis`, `training` | `AnalysisStatus` | `analysis_results.status` | 분석 작업 진행 상태이다. |
| 분석 결과 outcome | `analysis` | `AnalysisOutcome` | `analysis_results.analysis_outcome` | 완료된 worker 분석의 안전한 결과 의미다. |
| 분석 요청 outbox 상태 | `analysis` | `AnalysisRequestOutboxStatus` | `analysis_request_outbox.status` | DB와 Redis Stream 사이 dispatch 상태다. |
| 세그먼트 결과 상태 | `analysis` | `SegmentResultStatus` | `analysis_segments.result_status` | 문장/구간별 평가 상태이다. |
| 세그먼트 매칭 유형 | `analysis` | `SegmentMatchType` | `analysis_segments.match_type` | 예상 문장과 인식 문장의 매칭 유형이다. |
| 말하기 속도 상태 | `analysis` | `SpeedStatus` | `analysis_results.speed_status` | 말하기 속도 평가 결과이다. |
| 클래스 진행 상태 | `course` | `CourseProgressStatus` | `user_course_progress.status` | 사용자별 클래스 진행 상태이다. |
| 클래스 유형 | `course` | `CourseType` | `courses.course_type` | 발음/억양 클래스 유형이다. |
| 클래스 단계 유형 | `course` | `CourseStepType` | `course_steps.step_type` | 클래스 단계의 학습 방식이다. |

## Enum 값

| Enum | Value | Meaning | Terminal | Notes |
| --- | --- | --- | --- | --- |
| `UserStatus` | `ACTIVE` | 정상 사용 가능 계정 | No | 기본 사용자 상태 |
| `UserStatus` | `SUSPENDED` | 이용 제한 계정 | No | 로그인 또는 API 접근 제한 대상 |
| `UserStatus` | `WITHDRAWN` | 탈퇴 계정 | Yes | 일반 사용 흐름에서 제외 |
| `UserRole` | `USER` | 일반 사용자 | No | 기본 역할 |
| `UserRole` | `ADMIN` | 관리자 | No | 관리자 API가 생기면 권한 기준으로 사용 |
| `OAuthProvider` | `GOOGLE` | Google OAuth | No | 소셜 로그인 provider |
| `OAuthProvider` | `KAKAO` | Kakao OAuth | No | 소셜 로그인 provider |
| `OAuthProvider` | `NAVER` | Naver OAuth | No | 소셜 로그인 provider |
| `OAuthProvider` | `APPLE` | Apple OAuth | No | 소셜 로그인 provider |
| `CurrentLevel` | `BEGINNER` | 초급 학습자 | No | 온보딩 학습 수준 |
| `CurrentLevel` | `INTERMEDIATE` | 중급 학습자 | No | 온보딩 학습 수준 |
| `CurrentLevel` | `ADVANCED` | 고급 학습자 | No | 온보딩 학습 수준 |
| `PublishStatus` | `DRAFT` | 작성 중 | No | 공개 조회 대상 아님 |
| `PublishStatus` | `PUBLISHED` | 게시됨 | No | 일반 사용자 조회 가능 |
| `PublishStatus` | `HIDDEN` | 숨김 | No | 일반 사용자 조회 대상 아님 |
| `ContentType` | `NEWS` | 뉴스 콘텐츠 | No | 학습 콘텐츠 유형 |
| `ContentType` | `SENTENCE` | 문장 콘텐츠 | No | 학습 콘텐츠 유형 |
| `ContentType` | `ANNOUNCER` | 아나운서 연습 콘텐츠 | No | 학습 콘텐츠 유형 |
| `ContentType` | `CLASS_PRACTICE` | 클래스 연계 연습 콘텐츠 | No | 학습 콘텐츠 유형 |
| `Difficulty` | `BEGINNER` | 초급 | No | 콘텐츠/클래스 난이도 |
| `Difficulty` | `INTERMEDIATE` | 중급 | No | 콘텐츠/클래스 난이도 |
| `Difficulty` | `ADVANCED` | 고급 | No | 콘텐츠/클래스 난이도 |
| `LearningFocus` | `PRONUNCIATION` | 발음 중심 | No | 학습 초점 |
| `LearningFocus` | `INTONATION` | 억양 중심 | No | 학습 초점 |
| `LearningFocus` | `BOTH` | 발음과 억양 모두 | No | 학습 초점 |
| `SpeakerType` | `ANNOUNCER` | 아나운서 음성 | No | 기준 음성 유형 |
| `SpeakerType` | `COACH` | 코치 음성 | No | 기준 음성 유형 |
| `SpeakerType` | `TTS` | 합성 음성 | No | 기준 음성 유형 |
| `TrainingSessionStatus` | `RECORDING` | 녹음 진행 중 | No | 세션 생성 직후 기본 상태 |
| `TrainingSessionStatus` | `UPLOADING` | 녹음 업로드 중 | No | 업로드 흐름에서 사용 가능 |
| `TrainingSessionStatus` | `ANALYZING` | 분석 진행 중 | No | 분석 요청 후 상태 |
| `TrainingSessionStatus` | `COMPLETED` | 학습 완료 | Yes | 완료된 학습 세션 |
| `TrainingSessionStatus` | `FAILED` | 학습 흐름 실패 | Yes | 복구 정책 필요 |
| `TrainingSessionStatus` | `CANCELED` | 사용자가 취소 | Yes | 취소된 학습 세션 |
| `RecordingQualityStatus` | `PENDING` | 과거 비동기 품질 검사 대기 | No | 신규 backend-normalized 등록에서는 생성하지 않음 |
| `RecordingQualityStatus` | `PASS` | 분석 가능 | No | 분석 요청 가능 |
| `RecordingQualityStatus` | `LOW_VOLUME` | 음량 부족 | Yes | 재녹음 필요 |
| `RecordingQualityStatus` | `TOO_NOISY` | 잡음 과다 | Yes | 재녹음 필요 |
| `RecordingQualityStatus` | `TOO_SHORT` | 녹음 길이 부족 | Yes | 재녹음 필요 |
| `RecordingQualityStatus` | `NO_SPEECH` | 음성 미감지 | Yes | 재녹음 필요 |
| `RecordingQualityStatus` | `FAILED` | 품질 검사 실패 | Yes | 재녹음 또는 재처리 필요 |
| `AnalysisStatus` | `PENDING` | 분석 대기 | No | 분석 요청 생성 직후 |
| `AnalysisStatus` | `PROCESSING` | 분석 처리 중 | No | 외부 분석 진행 중 |
| `AnalysisStatus` | `COMPLETED` | 분석 완료 | Yes | 결과 조회 가능 |
| `AnalysisStatus` | `FAILED` | 분석 실패 | Yes | 재시도 가능 대상 |
| `AnalysisOutcome` | `COACHING_READY` | 근거 제한 코칭 가능 | Yes | `COMPLETED` 분석 결과 |
| `AnalysisOutcome` | `COMPLETED_NO_ISSUE` | 관찰 이슈 없음 | Yes | `COMPLETED` 분석 결과 |
| `AnalysisOutcome` | `RERECORD_REQUIRED` | 재녹음 필요 | Yes | `COMPLETED` 분석 결과 |
| `AnalysisOutcome` | `UNCERTAIN` | 근거 부족 | Yes | `COMPLETED` 분석 결과 |
| `AnalysisOutcome` | `FAILED_CLOSED` | 안전상 피드백 생략 | Yes | `COMPLETED` 분석 결과 |
| `AnalysisRequestOutboxStatus` | `PENDING` | Redis 발행 대기 | No | retry 가능 |
| `AnalysisRequestOutboxStatus` | `PUBLISHED` | Redis XADD 완료 | Yes | worker ACK와 별개 |
| `AnalysisRequestOutboxStatus` | `FAILED` | dispatch 재시도 소진 | Yes | 같은 request generation의 분석도 실패 처리 |
| `SegmentResultStatus` | `NORMAL` | 정상 | No | 개선 필요 낮음 |
| `SegmentResultStatus` | `CAUTION` | 주의 | No | 일부 개선 필요 |
| `SegmentResultStatus` | `NEEDS_IMPROVEMENT` | 개선 필요 | No | 우선 개선 대상 |
| `SegmentMatchType` | `MATCH` | 일치 | No | 예상 문장과 인식 문장 일치 |
| `SegmentMatchType` | `SUBSTITUTION` | 대체 | No | 다른 단어/음절로 인식 |
| `SegmentMatchType` | `OMISSION` | 누락 | No | 말하지 않았거나 인식 누락 |
| `SegmentMatchType` | `ADDITION` | 추가 | No | 예상 외 발화 또는 인식 추가 |
| `SpeedStatus` | `TOO_SLOW` | 너무 느림 | No | 속도 피드백 대상 |
| `SpeedStatus` | `NORMAL` | 적정 속도 | No | 정상 범위 |
| `SpeedStatus` | `TOO_FAST` | 너무 빠름 | No | 속도 피드백 대상 |
| `CourseProgressStatus` | `NOT_STARTED` | 시작 전 | No | 진도 record가 없거나 초기 상태 |
| `CourseProgressStatus` | `IN_PROGRESS` | 진행 중 | No | 클래스 학습 중 |
| `CourseProgressStatus` | `COMPLETED` | 완료 | Yes | 클래스 완료 |
| `CourseType` | `PRONUNCIATION` | 발음 클래스 | No | 클래스 유형 |
| `CourseType` | `INTONATION` | 억양 클래스 | No | 클래스 유형 |
| `CourseStepType` | `THEORY` | 이론 단계 | No | 클래스 단계 유형 |
| `CourseStepType` | `AUDIO_EXAMPLE` | 예시 음성 단계 | No | 클래스 단계 유형 |
| `CourseStepType` | `PRACTICE` | 연습 단계 | No | 클래스 단계 유형 |
| `CourseStepType` | `RESULT_REVIEW` | 결과 확인 단계 | No | 클래스 단계 유형 |

## 상태 전이 규칙

| From | To | Trigger | Validator | Side Effects |
| --- | --- | --- | --- | --- |
| none | `UserStatus.ACTIVE` | 회원가입 또는 소셜 신규 사용자 생성 | email/nickname 중복 없음 | `users` 생성 |
| `UserStatus.ACTIVE` | `UserStatus.SUSPENDED` | 관리자 이용 제한 | 관리자 권한 | API 접근 제한 |
| `UserStatus.ACTIVE` | `UserStatus.WITHDRAWN` | 회원 탈퇴 | 본인 계정 | `deleted_at` 기록, 후속 데이터 정리 대상 |
| none | onboarding completed | `PUT /api/onboarding/me` | 필수 설문 응답 존재 | `onboarding_profiles`, `survey_answers` 저장 |
| onboarding completed | onboarding updated | `PATCH /api/onboarding/me` | 수정 허용 필드 | 온보딩 목표/응답 일부 갱신 |
| none | `TrainingSessionStatus.RECORDING` | `POST /api/training-sessions` | 콘텐츠 존재, 게시/학습 가능 | `training_sessions` 생성 |
| `RECORDING` | `UPLOADING` | 녹음 업로드 URL 발급 또는 업로드 시작 | 세션 소유권, 파일 조건 | presigned URL 발급 |
| `RECORDING` or `UPLOADING` | `ANALYZING` | `POST /api/training-sessions/{sessionId}/analyze` | 최종 녹음 선택, 품질 `PASS`, 분석 중복 없음 | `analysis_results`와 request outbox를 한 transaction으로 생성 |
| `ANALYZING` | `COMPLETED` | `POST /api/training-sessions/{sessionId}/complete` | 선택 녹음의 분석 완료 | 완료 시간 저장 |
| `RECORDING` or `UPLOADING` or `ANALYZING` | `CANCELED` | `POST /api/training-sessions/{sessionId}/cancel` | terminal 상태 아님 | 취소 시간 저장, active processing consent 철회, 모든 세션 녹음과 미완료 upload intent를 durable 삭제 outbox에 기록 |
| any non-terminal | `FAILED` | 내부 처리 실패 | 실패 사유 존재 | 실패 사유 기록 대상 |
| none | `RecordingQualityStatus.PASS` | backend media normalization | owner/consent/container/codec/digest 및 기술 QC 통과 | canonical WAV 등록, 분석 요청 가능 |
| none | `LOW_VOLUME` | backend media normalization | RMS 기준 미달 | canonical WAV 등록, 재녹음 안내 |
| none | `TOO_SHORT` | backend media normalization | 측정 duration 기준 미달 | canonical WAV 등록, 재녹음 안내 |
| none | `NO_SPEECH` | backend media normalization | RMS와 active-sample 기준 미달 | canonical WAV 등록, 재녹음 안내 |
| none | `FAILED` | backend media normalization | clipping 등 기술 QC 실패 | canonical WAV 등록, 재녹음 안내 |
| none | request rejected | media normalization failure | 소유권·동의·container/codec·cleanup 불충족 | DB row를 만들지 않고 원본/실패 canonical 삭제 |
| none | `AnalysisStatus.PENDING` | 분석 요청 생성 | 선택 녹음 존재 | request generation UUID와 durable outbox 생성 |
| `PENDING` | `PROCESSING` | AI worker processing result 수신 | `requestEventId` 일치 | 현재 generation의 상태만 갱신 |
| `PENDING` or `PROCESSING` | `COMPLETED` | AI worker terminal success 수신 | outcome 및 payload schema 검증 | 결과와 segment를 원자 반영 |
| `PENDING` or `PROCESSING` | `FAILED` | AI worker 실패, outbox retry 소진, 또는 결과 delivery retry 소진 | `requestEventId`와 안전한 실패 코드 일치 | retry 가능 상태 |
| `FAILED` | `PENDING` | `POST /api/training-sessions/{sessionId}/analysis/retry` | 실패 상태, 영속 retry count 3회 제한 | 잠근 분석 행의 retry count 증가, 새 request generation과 outbox record 생성 |
| none | `CourseProgressStatus.NOT_STARTED` | 진도 조회 시 record 없음 | 사용자/클래스 존재 | 응답용 기본 상태 |
| none | `CourseProgressStatus.IN_PROGRESS` | `POST /api/courses/{courseId}/start` | 클래스 존재, 게시 상태 | `user_course_progress` 생성 |
| `NOT_STARTED` | `IN_PROGRESS` | 진도 갱신 | 유효한 step/progress | 시작 상태로 전환 |
| `IN_PROGRESS` | `COMPLETED` | `POST /api/courses/{courseId}/complete` | 필수 단계 완료 | 완료 시간 저장 |

## 인증 상태

| State | Source of Truth | Meaning | Notes |
| --- | --- | --- | --- |
| Unauthenticated | request에 유효한 access token 없음 | 로그인하지 않은 사용자 | public API만 접근 가능 |
| Authenticated | 유효한 access token | 로그인 사용자 | 대부분의 API 접근 가능 |
| Access Token Expired | access token 만료 | access token 재발급 필요 | refresh token으로 갱신 시도 |
| Refresh Token Valid | `refresh_tokens` 저장 값과 만료 시간 | access token 재발급 가능 | token refresh API 사용 |
| Refresh Token Expired | refresh token 만료 또는 폐기 | 세션 종료 | 다시 로그인 필요 |
| Forbidden | 인증은 되었으나 권한 또는 소유권 없음 | 접근 차단 | 403 응답 |
| Admin | `UserRole.ADMIN` | 관리자 권한 | 관리자 API가 생기면 사용 |

## 상태 변경 체크리스트

- enum 값을 추가/삭제/이름 변경했는가?
- DB column 값과 migration이 필요한가?
- API request/response 문서의 허용 값이 갱신되었는가?
- 상태 전이 validator와 error code가 정의되었는가?
- terminal 상태에서 허용되지 않는 명령이 차단되는가?
- 조회 API의 empty state와 error state가 명확한가?
- 프론트엔드가 문자열 enum 값을 그대로 사용해도 되는가?
- 테스트 데이터와 fixture의 enum 값이 갱신되었는가?
