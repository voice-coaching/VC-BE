# Canonical v4 journal/receipt JSON 응답 수정

2026-10-02 실제 `testvideo1` 음성 API 분석 51에서 core는 `ACCEPT / ok`로 끝났지만
journal status 응답의 중첩 association/metadata가 JSON 값 대신 JsonNode의
`nodeType`, `object`, `array` 등 bean 속성으로 전송됐다. 근거 5개가 PREPARED인 시점에
RunPod의 공유 JSON schema 검증이 이를 거부했다. B2 업로드나 callback 완료 전 실패다.

## 원인

RunPodContract와 frozen/schema 검증기는 Jackson 2 (`com.fasterxml.jackson`)를 사용하고,
Spring Boot 4 MVC는 Jackson 3를 기본 사용한다. 다른 계열의 JsonNode를 HTTP 응답 객체에
포함하면 원래 JSON 트리가 유지되지 않는다. journal의 null/empty 초기 응답만으로는
발견되지 않았고, 실제 분석 후 중첩 근거가 생겼을 때 재현됐다.
[Spring Boot JSON 공식 문서](https://docs.spring.io/spring-boot/reference/features/json.html)

## 수정 범위

- CanonicalJournalController reserve/status: `RunPodContract.encode(..., "journalSnapshot")`
  로 계약 검증된 UTF-8 JSON bytes를 `application/json`으로 반환.
- CanonicalEvidenceController register/status: 동일 방식으로 `evidenceReceipt`를 반환.
  receipt의 association에도 같은 JsonNode가 있어 후속 단계 실패를 함께 방지.
- HTTP 경로, 필드·숫자·null 의미, status code, Cache-Control, Retry-After, 서버 인증과
  worker 소유 관계는 유지. 전역 MVC ObjectMapper나 기존 공개 API 설정은 변경하지 않는다.
- schema, DB migration, 키, nginx, FE 변경 없음.

## 확인 범위와 배포 기준

- 기준: 실제 운영 소스 `a241a99` (develop `b410076` 위의 기존 canonical rollout).
  rollout 커밋이 develop에 없어 이 수정 브랜치는 해당 선행 구현을 포함한다.
- `bootJar -x test` 컴파일·패키징 성공. 자동 회귀 테스트/브라우저 QA는 실행하지 않았다.
- 운영 JAR `7f9f4c988c81fe7e0c73ba0fce52db1db1482519f43fb39fcc0b08c50073ce12`와
  새 JAR의 BOOT-INF 패키지별 해시를 대조했다. 변경은 analysis/controller 패키지에만 있고,
  나머지 클래스·리소스·의존성은 일치한다.
- 이 문서 최초 작성 시점에는 운영 JAR 교체와 API 재시도 완료를 아직 확인하지 않았다.
  테스트 계정 40/세션 97/녹음 61만 사용하며 실패한 분석을 DB에서 되살리지 않는다.
  Backend의 정상 retry API로 새로운 실행을 요청한다.
