# Voice

발음 및 억양 학습을 위한 Spring Boot 기반 백엔드 프로젝트입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.0
- Gradle
- Spring Data JPA
- PostgreSQL
- JWT Authentication

## 주요 기능

- 일반 회원가입 및 로그인
- SNS 로그인
- JWT Access Token / Refresh Token 인증
- 내 정보 조회, 프로필 수정, 회원 탈퇴
- 온보딩 설문 저장 및 조회
- 학습 콘텐츠 및 기준 음성 조회
- 학습 세션 생성
- 음성 녹음 업로드 및 최종 녹음 선택
- 음성 분석 요청 및 분석 결과 조회
- 클래스 목록, 상세, 단계, 진도, 완료 관리
- 홈 대시보드, 개인화 추천, 최근 학습 조회

## 프로젝트 구조

```text
src/main/java/org/example/voice
+-- VoiceApplication.java
+-- auth
+-- user
+-- onboarding
+-- practicecontent
+-- training
+-- analysis
+-- course
+-- home
+-- common
```

## 패키지 규칙

각 도메인 패키지는 기본적으로 아래 구조를 따릅니다.

```text
controller        # API 요청/응답 처리
application       # 비즈니스 로직
domain            # Entity, 도메인 모델
infrastructure    # Repository, 외부 연동 구현
dto               # Request/Response DTO
exception         # 도메인별 예외
```

공통 기능은 `common` 패키지에 둡니다.

```text
common
+-- config
+-- response
+-- exception
+-- enums
+-- security
+-- storage
+-- util
```

## 실행 방법

macOS / Linux:

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

## 테스트

macOS / Linux:

```bash
./gradlew test
```

Windows:

```bash
gradlew.bat test
```

## 환경 설정

기본 설정 파일은 아래 경로를 사용합니다.

```text
src/main/resources/application.yaml
```

로컬 환경 변수와 비밀값은 Git에 올리지 않습니다.

```text
.env
src/main/resources/application-local.yaml
src/main/resources/application-secret.yaml
```

## 문서

- API 명세서: https://app.notion.com/p/697fd927f58c820f99850163cde5d6a2
- ERD: https://app.notion.com/p/ERD-3b5fd927f58c80a48f6bd83fc7e1629c

## 커밋 컨벤션

```text
FEAT: 새로운 기능 추가
FIX: 버그 수정
CHORE: 설정, 빌드, 패키지 구조 변경
DOCS: 문서 수정
REFACTOR: 리팩터링
TEST: 테스트 추가/수정
```

예시:

```bash
git commit -m "FEAT: add signup API"
git commit -m "CHORE: add package directory structure"
git commit -m "DOCS: add project README"
```
