# Endpoint List - voice

This document summarizes the API list exported from the local API spec directory. See [specification.md](specification.md) for request/response details.

| Method | URL | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/auth/email-availability` | public | 이메일 중복 확인 - Query: email. 사용 가능한 이메일이면 available=true 반환 |
| POST | `/api/auth/signup` | public | 일반 회원가입 - 이메일·비밀번호·닉네임·약관 동의 시각을 받아 회원 생성 후 온보딩 필요 여부 반환 |
| POST | `/api/auth/login` | public | 일반 로그인 - 이메일·비밀번호 검증 후 Access Token과 Refresh Token 발급 |
| POST | `/api/auth/social-login` | public | SNS 로그인 - provider와 authorizationCode를 받아 기존 회원 로그인 또는 신규 회원 생성. 신규 회원 여부 반환 |
| POST | `/api/auth/token/refresh` | public | 토큰 갱신 - Refresh Token 쿠키 또는 요청값을 검증하여 Access Token 재발급 |
| POST | `/api/auth/logout` | Bearer accessToken | 로그아웃 - 현재 세션 종료 및 Refresh Token 무효화 |
| GET | `/api/users/me` | Bearer accessToken | 내 정보 조회 - 로그인한 사용자의 이메일·닉네임·가입 방식·온보딩 완료 여부 조회 |
| PATCH | `/api/users/me` | Bearer accessToken | 프로필 수정 - 닉네임 등 수정 가능한 사용자 기본 정보 변경 |
| DELETE | `/api/users/me` | Bearer accessToken | 회원 탈퇴 - 회원 상태를 탈퇴 처리하고 사용자 녹음 파일 및 연관 데이터 삭제 절차 수행 |
| DELETE | `/api/training-sessions/{sessionId}/recordings/{recordingId}` | Bearer accessToken | 녹음 시도 삭제 - 재녹음으로 버린 파일을 DB와 스토리지에서 삭제. 분석 완료 녹음은 정책에 따라 제한 |
| GET | `/api/onboarding/me` | Bearer accessToken | 온보딩 정보 조회 - 현재 수준·학습 목표·목표 학습량·설문 응답 JSON 조회 |
| POST | `/api/training-sessions/{sessionId}/analyze` | Bearer accessToken | 음성 분석 요청 - 선택된 녹음의 음질을 확인하고 STT·발음·억양 분석 작업을 비동기로 요청 |
| PUT | `/api/onboarding/me` | Bearer accessToken | 온보딩 저장 및 완료 - 온보딩 전체 응답을 저장하고 completedAt을 기록. 최초 완료와 재저장 모두 지원 |
| GET | `/api/training-sessions/{sessionId}/analysis/status` | Bearer accessToken | 분석 진행 상태 조회 - PENDING·PROCESSING·COMPLETED·FAILED 상태와 진행 단계 반환 |
| PATCH | `/api/onboarding/me` | Bearer accessToken | 온보딩 일부 수정 - 학습 목적·개선 영역·목표 학습량 등 일부 항목만 변경 |
| POST | `/api/training-sessions/{sessionId}/analysis/retry` | Bearer accessToken | 분석 재시도 - 일시적 오류로 실패한 분석 작업을 같은 최종 녹음으로 다시 요청 |
| GET | `/api/home` | Bearer accessToken | 홈 대시보드 조회 - 오늘의 학습 현황·추천 카드·최근 학습·클래스 진행률을 한 번에 반환 |
| POST | `/api/training-sessions/{sessionId}/complete` | Bearer accessToken | 학습 세션 완료 - 분석 완료 여부를 확인하고 학습 시간과 완료 일시를 저장 |
| GET | `/api/recommendations` | Bearer accessToken | 개인화 추천 목록 조회 - Query: type, limit. 온보딩과 최근 분석 결과를 기준으로 추천 콘텐츠 반환 |
| POST | `/api/training-sessions/{sessionId}/cancel` | Bearer accessToken | 학습 세션 취소 - 진행 중 세션을 CANCELED로 변경하고 미사용 임시 녹음 삭제 대상으로 처리 |
| GET | `/api/users/me/training-sessions/recent` | Bearer accessToken | 최근 학습 이어하기 조회 - 가장 최근의 미완료 학습 또는 완료 직전 학습 정보를 반환 |
| GET | `/api/recordings/{recordingId}/playback-url` | Bearer accessToken | 사용자 녹음 재생 URL 조회 - 본인 녹음인지 확인한 뒤 과거 녹음을 재생할 수 있는 제한시간 URL 반환 |
| GET | `/api/practice-contents` | Bearer accessToken | 학습 콘텐츠 목록 조회 - Query: type, category, difficulty, focus, page, size. NEWS·SENTENCE·ANNOUNCER·CLASS_PRACTICE 공통 목록 |
| GET | `/api/analyses/{analysisId}` | Bearer accessToken | 종합 분석 결과 조회 - STT 문장·전체 점수·발음·억양·속도·강세·휴지·강점·약점·종합 피드백 반환 |
| GET | `/api/practice-contents/{contentId}` | Bearer accessToken | 학습 콘텐츠 상세 조회 - 제목·스크립트·난이도·학습 초점·뉴스 출처·목표 발음 항목을 반환 |
| GET | `/api/analyses/{analysisId}/segments` | Bearer accessToken | 음절별 분석 결과 조회 - Query: page, size. 원문·인식문·재생 시간·일치 유형·점수·상세 피드백 반환 |
| GET | `/api/practice-contents/{contentId}/reference-audios` | Bearer accessToken | 기준 음성 목록 조회 - 콘텐츠에 연결된 아나운서·코치·TTS 기준 음성 목록 반환 |
| GET | `/api/training-sessions/{sessionId}/analysis` | Bearer accessToken | 학습 세션 분석 결과 조회 - 세션 ID로 최종 분석 결과와 analysisId를 조회하여 결과 화면 진입에 사용 |
| GET | `/api/reference-audios/{audioId}/playback-url` | Bearer accessToken | 기준 음성 재생 URL 조회 - 권한 검증 후 제한시간이 있는 재생 URL 또는 CDN URL 반환 |
| POST | `/api/analyses/{analysisId}/feedback/regenerate` | Bearer accessToken | 분석 결과 요약 재생성 - 음절별 분석 데이터는 유지하고 AI 종합 피드백만 재생성. 운영 정책에 따라 횟수 제한 가능 |
| GET | `/api/practice-contents/next` | Bearer accessToken | 다음 학습 콘텐츠 조회 - Query: type, category, difficulty, excludeId. 현재 콘텐츠 다음에 연습할 항목 반환 |
| GET | `/api/users/me/training-sessions` | Bearer accessToken | 학습 기록 목록 조회 - Query: type, status, from, to, page, size. 완료된 학습 기록을 최신순으로 조회 |
| GET | `/api/practice-contents/{contentId}/recommendations` | Bearer accessToken | 콘텐츠 기반 추천 조회 - 현재 콘텐츠의 난이도·발음 유형과 유사한 다음 콘텐츠 목록 반환 |
| GET | `/api/users/me/training-sessions/{sessionId}` | Bearer accessToken | 학습 기록 상세 조회 - 콘텐츠·최종 녹음·STT·종합 결과·음절별 피드백을 한 번에 반환 |
| GET | `/api/courses` | Bearer accessToken | 클래스 목록 조회 - Query: type, difficulty, status, page, size. 발음·억양 클래스 목록 반환 |
| DELETE | `/api/users/me/training-sessions/{sessionId}` | Bearer accessToken | 학습 기록 삭제 - 사용자 요청에 따라 학습 기록과 연결된 녹음 파일 및 분석 결과 삭제 |
| GET | `/api/courses/{courseId}` | Bearer accessToken | 클래스 상세 조회 - 클래스 설명·난이도·예상 시간·사용자 진행률 요약 반환 |
| GET | `/api/users/me/statistics` | Bearer accessToken | 학습 통계 조회 - Query: period, from, to. 누적 횟수·누적 시간·오늘 현황·연속 학습일·평균 점수 반환 |
| GET | `/api/courses/{courseId}/steps` | Bearer accessToken | 클래스 단계 목록 조회 - 이론·예시 듣기·녹음 연습·결과 확인 단계와 연결 콘텐츠 반환 |
| GET | `/api/users/me/strengths-weaknesses` | Bearer accessToken | 강점 및 약점 조회 - Query: period, limit. analysis_segments를 집계하여 잘하는 발음과 반복 오류 항목 반환 |
| POST | `/api/courses/{courseId}/start` | Bearer accessToken | 클래스 시작 - 사용자 클래스 진도 레코드를 생성하거나 기존 진도를 이어서 반환 |
| GET | `/api/users/me/score-trends` | Bearer accessToken | 점수 변화 추이 조회 - Query: metric, period. 발음·억양·종합 점수의 날짜별 변화 데이터를 반환 |
| GET | `/api/users/me/course-progress` | Bearer accessToken | 내 클래스 진행 목록 조회 - Query: status. 사용자가 시작하거나 완료한 클래스와 진행률 조회 |
| GET | `/api/users/me/weakness-recommendations` | Bearer accessToken | 약점 기반 추천 조회 - 최근 약점 분석을 기준으로 문장·뉴스·클래스 추천 목록 반환 |
| GET | `/api/courses/{courseId}/progress` | Bearer accessToken | 클래스 진행 상태 조회 - 현재 상태·마지막 단계·진행률·완료 일시 조회 |
| PATCH | `/api/courses/{courseId}/progress` | Bearer accessToken | 클래스 진행 상태 수정 - lastStepId와 progressPercent를 갱신하여 이어서 학습 기능 제공 |
| POST | `/api/courses/{courseId}/complete` | Bearer accessToken | 클래스 완료 처리 - 모든 필수 단계 완료 여부를 확인한 뒤 COMPLETED 상태로 변경 |
| POST | `/api/training-sessions` | Bearer accessToken | 학습 세션 생성 - contentId·courseStepId(선택)·learningFocus를 받아 학습 세션 생성 |
| GET | `/api/training-sessions/{sessionId}` | Bearer accessToken | 학습 세션 조회 - 세션 상태·콘텐츠·녹음 시도·분석 가능 여부를 조회 |
| POST | `/api/training-sessions/{sessionId}/recordings/upload-url` | Bearer accessToken | 녹음 업로드 URL 발급 - 파일명·MIME 타입을 받아 S3 또는 오브젝트 스토리지 Presigned URL 발급 |
| POST | `/api/training-sessions/{sessionId}/recordings` | Bearer accessToken | 녹음 업로드 완료 등록 - 업로드된 객체 키·재생 시간·파일 크기를 등록하고 녹음 시도 번호 생성 |
| GET | `/api/training-sessions/{sessionId}/recordings` | Bearer accessToken | 녹음 시도 목록 조회 - 해당 학습 세션의 녹음 시도와 품질 검사 상태 조회 |
| PATCH | `/api/training-sessions/{sessionId}/recordings/{recordingId}/select` | Bearer accessToken | 최종 녹음 선택 - 분석에 사용할 최종 녹음을 선택하고 다른 시도는 선택 해제 |

## By Category

### 인증

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/auth/email-availability` | public | 시작 전 | 이메일 중복 확인 |
| POST | `/api/auth/signup` | public | 시작 전 | 일반 회원가입 |
| POST | `/api/auth/login` | public | 시작 전 | 일반 로그인 |
| POST | `/api/auth/social-login` | public | 시작 전 | SNS 로그인 |
| POST | `/api/auth/token/refresh` | public | 시작 전 | 토큰 갱신 |
| POST | `/api/auth/logout` | Bearer accessToken | 시작 전 | 로그아웃 |

### 사용자

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/users/me` | Bearer accessToken | 완료 | 내 정보 조회 |
| PATCH | `/api/users/me` | Bearer accessToken | 완료 | 프로필 수정 |
| DELETE | `/api/users/me` | Bearer accessToken | 완료 | 회원 탈퇴 |

### 학습

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| DELETE | `/api/training-sessions/{sessionId}/recordings/{recordingId}` | Bearer accessToken | 완료 | 녹음 시도 삭제 |
| POST | `/api/training-sessions/{sessionId}/analyze` | Bearer accessToken | 완료 | 음성 분석 요청 |
| GET | `/api/training-sessions/{sessionId}/analysis/status` | Bearer accessToken | 완료 | 분석 진행 상태 조회 |
| POST | `/api/training-sessions/{sessionId}/analysis/retry` | Bearer accessToken | 완료 | 분석 재시도 |
| POST | `/api/training-sessions/{sessionId}/complete` | Bearer accessToken | 완료 | 학습 세션 완료 |
| POST | `/api/training-sessions/{sessionId}/cancel` | Bearer accessToken | 완료 | 학습 세션 취소 |
| GET | `/api/recordings/{recordingId}/playback-url` | Bearer accessToken | 완료 | 사용자 녹음 재생 URL 조회 |
| POST | `/api/training-sessions` | Bearer accessToken | 완료 | 학습 세션 생성 |
| GET | `/api/training-sessions/{sessionId}` | Bearer accessToken | 완료 | 학습 세션 조회 |
| POST | `/api/training-sessions/{sessionId}/recordings/upload-url` | Bearer accessToken | 완료 | 녹음 업로드 URL 발급 |
| POST | `/api/training-sessions/{sessionId}/recordings` | Bearer accessToken | 완료 | 녹음 업로드 완료 등록 |
| GET | `/api/training-sessions/{sessionId}/recordings` | Bearer accessToken | 완료 | 녹음 시도 목록 조회 |
| PATCH | `/api/training-sessions/{sessionId}/recordings/{recordingId}/select` | Bearer accessToken | 완료 | 최종 녹음 선택 |

### 온보딩

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/onboarding/me` | Bearer accessToken | 완료 | 온보딩 정보 조회 |
| PUT | `/api/onboarding/me` | Bearer accessToken | 완료 | 온보딩 저장 및 완료 |
| PATCH | `/api/onboarding/me` | Bearer accessToken | 완료 | 온보딩 일부 수정 |

### 홈

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/home` | Bearer accessToken | 완료 | 홈 대시보드 조회 |
| GET | `/api/recommendations` | Bearer accessToken | 완료 | 개인화 추천 목록 조회 |
| GET | `/api/users/me/training-sessions/recent` | Bearer accessToken | 완료 | 최근 학습 이어하기 조회 |

### 학습 컨텐츠

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/practice-contents` | Bearer accessToken | 완료 | 학습 콘텐츠 목록 조회 |
| GET | `/api/practice-contents/{contentId}` | Bearer accessToken | 완료 | 학습 콘텐츠 상세 조회 |
| GET | `/api/practice-contents/{contentId}/reference-audios` | Bearer accessToken | 완료 | 기준 음성 목록 조회 |
| GET | `/api/reference-audios/{audioId}/playback-url` | Bearer accessToken | 완료 | 기준 음성 재생 URL 조회 |
| GET | `/api/practice-contents/next` | Bearer accessToken | 완료 | 다음 학습 콘텐츠 조회 |
| GET | `/api/practice-contents/{contentId}/recommendations` | Bearer accessToken | 완료 | 콘텐츠 기반 추천 조회 |

### 분석결과

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/analyses/{analysisId}` | Bearer accessToken | 시작 전 | 종합 분석 결과 조회 |
| GET | `/api/analyses/{analysisId}/segments` | Bearer accessToken | 시작 전 | 음절별 분석 결과 조회 |
| GET | `/api/training-sessions/{sessionId}/analysis` | Bearer accessToken | 시작 전 | 학습 세션 분석 결과 조회 |
| POST | `/api/analyses/{analysisId}/feedback/regenerate` | Bearer accessToken | 시작 전 | 분석 결과 요약 재생성 |

### 마이페이지

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/users/me/training-sessions` | Bearer accessToken | 시작 전 | 학습 기록 목록 조회 |
| GET | `/api/users/me/training-sessions/{sessionId}` | Bearer accessToken | 시작 전 | 학습 기록 상세 조회 |
| DELETE | `/api/users/me/training-sessions/{sessionId}` | Bearer accessToken | 시작 전 | 학습 기록 삭제 |
| GET | `/api/users/me/statistics` | Bearer accessToken | 시작 전 | 학습 통계 조회 |
| GET | `/api/users/me/strengths-weaknesses` | Bearer accessToken | 시작 전 | 강점 및 약점 조회 |
| GET | `/api/users/me/score-trends` | Bearer accessToken | 시작 전 | 점수 변화 추이 조회 |
| GET | `/api/users/me/weakness-recommendations` | Bearer accessToken | 시작 전 | 약점 기반 추천 조회 |

### 클래스

| Method | URL | Auth | Implementation Status | Description |
| --- | --- | --- | --- | --- |
| GET | `/api/courses` | Bearer accessToken | 완료 | 클래스 목록 조회 |
| GET | `/api/courses/{courseId}` | Bearer accessToken | 완료 | 클래스 상세 조회 |
| GET | `/api/courses/{courseId}/steps` | Bearer accessToken | 완료 | 클래스 단계 목록 조회 |
| POST | `/api/courses/{courseId}/start` | Bearer accessToken | 완료 | 클래스 시작 |
| GET | `/api/users/me/course-progress` | Bearer accessToken | 완료 | 내 클래스 진행 목록 조회 |
| GET | `/api/courses/{courseId}/progress` | Bearer accessToken | 완료 | 클래스 진행 상태 조회 |
| PATCH | `/api/courses/{courseId}/progress` | Bearer accessToken | 완료 | 클래스 진행 상태 수정 |
| POST | `/api/courses/{courseId}/complete` | Bearer accessToken | 완료 | 클래스 완료 처리 |
