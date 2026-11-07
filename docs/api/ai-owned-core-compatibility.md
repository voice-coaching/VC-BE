# AI 소유 CORE와 Backend 결과 수신 호환성

2026-11-07 수정 후보. 기준 Backend `main`은 `8735617`, 실제 운영 AI의 소스 식별자는
`e0b2c5da98ac6bd90c343d31a15d1c48886f4f21`이다. 이 변경은 현재 legacy v6/handoff v2 수신을 수정한다.
[독립 제출·수집 경로](../architecture/independent-analysis-pull.md)의 활성화와는 별개다.

## 확인한 불일치

AWS의 분석 180·181은 AI claim/실행 시작 이후 handoff POST에서 각각 2026-11-07
14:15:06·14:16:36 UTC에 422를 받았다. 결과 인수/commit은 없고 이후 FAILED로 기록됐다.
181의 최초 503은 재시도 뒤 접수됐으므로 최종 인계 실패와 구분한다.

AI `/health/deployment`가 보고한 실제 CORE manifest는
`dae36e72646904bcdefc081654f67c06f80ff0565c384d439a5692e51ab7026b`다.
Backend main 결과 스키마의 CORE enum에는 이 값이 없었다. 같은 값을 결과에 전달하면
Backend schema validation이 거절하는 조건을 확인했다. 이 허용 목록 제거는 develop에 존재했지만
배포된 main에는 없었다.

AI의 두 계약 파일은 `/health/handoff`가 보고한 SHA와 해당 Git 객체의 실제 bytes SHA가 일치했다.
스키마 전체 SHA 차이만으로 특정 요청의 검증 실패 필드를 확정하지 않는다.
180·181 원본 인계 요청과 당시 세부 validator 오류는 이번 조사에서 확보하지 못했다.
따라서 이 수정은 확인한 호환성 결함을 제거하며, 기존 두 요청의 모든 실패 원인이나 운영 복구 완료를 소급 증명하지 않는다.

## Backend 수정

- `runpod_result_v6.schema.json`: binding, association, sourceIdentity, audioAssociation의
  parent CORE digest enum 4곳을 소문자 64 hex SHA-256 형식 검사로 변경한다.
- `runpod_result_v4.schema.json`: 기존 수신 호환 모델의 대응하는 enum 3곳도 같은 형식 검사로 변경한다.
- LLM/prompt/CORE 신규 release를 Backend 목록에 계속 추가하는 방식으로 운영하지 않는다.
  실제 모델과 자산의 선택·설치 검증은 AI 책임이다. 원본의 identity 값은 그대로 보존한다.
- `runpod_visual_coaching_input_v1.schema.json`: 운영 AI Git 객체의 정확한 계약을 반영한다.
  PROVISIONAL, LOWEST_12_SCORED_THEN_OBSERVED_V1, 선택적 COMPARE_REFERENCE를 지원하며
  기존 NONE·CALIBRATED·빈 action 목록은 유지한다. 영상 접수를 새로 활성화하지 않는다.
- `RunPodContract.validate`: 거절 시 `analysis_contract_rejected` 이벤트에 contract_kind,
  violation_count, 최대 8개의 제한된 field_paths만 남긴다. validator 메시지/인자·원고·결과 값·토큰은 기록하지 않는다.
  기존 HTTP 422/VALIDATION_FAILED 응답은 유지한다.

요청/실행/녹음 소유 관계, 미디어 digest, 전송 크기, 원본 인계 저장과 commit 경계는 이 수정에서 변경하지 않는다.
잘못된 digest 형식이나 기존 transport 필수 필드를 임의 성공 처리하지 않는다.
점수·공식 검산표·피드백을 다시 계산하거나 없는 값을 채우지 않는다.

## 계약 출처와 패키징

| 파일 | 후보 Backend SHA-256 | 출처·의미 |
| --- | --- | --- |
| runpod_result_v4.schema.json | `0ca8de08aec0a6403f3c8c87d539f69ada8127e147dd89b41ffcdedb46e5d99a` | 기존 Backend 수신 정의에 CORE enum 제거 |
| runpod_result_v6.schema.json | `09334a54c3b81c402a7b26136db5826e71fff0a0605338351e92327383033c6a` | main의 영상 확장 유지, CORE enum 제거 |
| runpod_visual_coaching_input_v1.schema.json | `63c560fdd1e775cdce21831ad840e63419f12e53ca5e70023d302bd95cdcdbde` | 운영 AI e0b2c5d와 정확한 bytes 일치 |

수신용 v6는 AI의 비공개 release/rubric gate를 복제하지 않으므로 전체 파일 SHA를 AI와 같게 만들지 않는다.
이 SHA는 배포 산출물 확인용이며 모델 허용 여부를 결정하는 값이 아니다.
Gradle processResources가 docs/contracts 원본을 JAR의 contracts/로 복사한다.

## 검증과 운영 범위

Java 21의 `compileJava compileTestJava bootJar -x test`와 패키징된 계약의 bytes/SHA,
변경 diff를 확인한다. 기존 패키징 검사의 두 digest 상수는 현재 계약으로 갱신하며 새 테스트/fixture는 추가하지 않는다.
자동 회귀 테스트·실제 사용자 녹음/추론/GPT·실패 요청 재분석은 수행하지 않는다.
운영 배포·설정 전환·기존 결과 재전송도 이번 로컬 수정에 포함하지 않는다.
운영 복구는 수정 JAR 배포 후 기존 정책에 따른 실제 결과 인수·commit·조회까지 별도로 확인해야 한다.
